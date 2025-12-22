package net.mysterria.stuff.features.chatcontrol;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Securely writes custom join/quit messages to ChatControl configuration files
 * Implements strict validation to prevent path traversal and command injection
 */
public class ChatControlFileWriter {

    private final MysterriaStuff plugin;

    // Security: Only allow alphanumeric, underscore, hyphen in player names (standard Minecraft username rules)
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_-]{1,16}$");

    // Security: Validate that we're only accessing the expected ChatControl directory
    private static final String CHATCONTROL_BASE = "plugins/ChatControl/messages";

    public ChatControlFileWriter(MysterriaStuff plugin) {
        this.plugin = plugin;
    }

    /**
     * Validates and writes custom join/quit messages for a player
     *
     * @param player The player
     * @param joinMessage The custom join message with %player% placeholder
     * @param quitMessage The custom quit message with %player% placeholder
     * @return true if successful, false otherwise
     */
    public boolean writePlayerMessages(Player player, String joinMessage, String quitMessage) {
        String username = player.getName();

        // Security: Validate username format
        if (!isValidUsername(username)) {
            PrettyLogger.warn("Invalid username format: " + username);
            return false;
        }

        // Security: Validate messages contain required placeholder
        if (!joinMessage.contains("%player%")) {
            player.sendMessage(ChatControlMessageManager.getInstance()
                    .getMessage("join-missing-placeholder"));
            return false;
        }

        if (!quitMessage.contains("%player%")) {
            player.sendMessage(ChatControlMessageManager.getInstance()
                    .getMessage("quit-missing-placeholder"));
            return false;
        }

        // Security: Sanitize messages to prevent injection
        joinMessage = sanitizeMessage(joinMessage);
        quitMessage = sanitizeMessage(quitMessage);

        try {
            // Get the plugins directory
            File pluginsDir = plugin.getDataFolder().getParentFile();
            if (pluginsDir == null || !pluginsDir.exists()) {
                PrettyLogger.warn("Failed to locate plugins directory");
                player.sendMessage(ChatControlMessageManager.getInstance()
                        .getMessage("write-error"));
                return false;
            }

            // Security: Construct paths safely and validate they're within expected directory
            Path joinPath = pluginsDir.toPath().resolve("ChatControl/messages/join.rs").normalize();
            Path quitPath = pluginsDir.toPath().resolve("ChatControl/messages/quit.rs").normalize();
            Path baseDir = pluginsDir.toPath().resolve("ChatControl/messages").normalize();

            // Security: Verify paths are within the ChatControl directory (prevent path traversal)
            if (!joinPath.startsWith(baseDir) || !quitPath.startsWith(baseDir)) {
                PrettyLogger.warn("Path traversal attempt detected for user: " + username);
                return false;
            }

            // Security: Verify directory exists
            if (!Files.exists(baseDir)) {
                player.sendMessage(ChatControlMessageManager.getInstance()
                        .getMessage("chatcontrol-not-found"));
                PrettyLogger.warn("ChatControl directory not found at: " + baseDir);
                return false;
            }

            // Write join message
            if (!writeOrUpdateMessage(joinPath.toFile(), username, joinMessage, "join")) {
                return false;
            }

            // Write quit message
            if (!writeOrUpdateMessage(quitPath.toFile(), username, quitMessage, "quit")) {
                return false;
            }

            // Reload ChatControl plugin
            return reloadChatControl(player);

        } catch (Exception e) {
            PrettyLogger.warn("Error writing ChatControl messages: " + e.getMessage());
            player.sendMessage(ChatControlMessageManager.getInstance()
                    .getMessage("write-error"));
            return false;
        }
    }

    /**
     * Security: Validates username against Minecraft username rules
     */
    private boolean isValidUsername(String username) {
        return VALID_USERNAME.matcher(username).matches();
    }

    /**
     * Security: Sanitizes message content to prevent injection attacks
     * Removes any characters that could be used for command injection or file system attacks
     */
    private String sanitizeMessage(String message) {
        // Remove any null bytes (common in injection attacks)
        message = message.replace("\0", "");

        // Remove or escape newlines that aren't part of the message list format
        // We'll handle proper newlines in the appendToFile method
        message = message.replace("\r", "");
        message = message.replace("\n", " ");

        // Remove any shell command characters that could be dangerous
        // These shouldn't appear in a normal message but prevent injection
        message = message.replace("`", "");
        message = message.replace("$(", "");
        message = message.replace("${", "");

        return message;
    }

    /**
     * Writes or updates a message configuration in a ChatControl file
     * If the player already has a section, it removes the old one and adds the new one
     */
    private boolean writeOrUpdateMessage(File file, String username, String message, String type) {
        try {
            // Create file if it doesn't exist
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            // Read all existing lines
            List<String> lines = new ArrayList<>();
            if (file.length() > 0) {
                lines = Files.readAllLines(file.toPath());
            }

            // Remove old section for this player if it exists
            String groupMarker = "group " + username + "-" + type + "-message";
            lines = removePlayerSection(lines, groupMarker);

            // Add the new message group
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
                lines.add(""); // Add blank line before new section
            }
            lines.add(groupMarker);
            lines.add("require sender script \"{player}\" == \"" + username + "\"");
            lines.add("message:");
            lines.add("- " + message.replace("%player%", "{player}"));

            // Write all lines back to file
            Files.write(file.toPath(), lines);

            PrettyLogger.debug("Successfully wrote " + type + " message for " + username);
            return true;

        } catch (IOException e) {
            PrettyLogger.warn("Failed to write to " + file.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes a player's section from the lines list
     * Removes from the group line until (but not including) the next group line or end of file
     */
    private List<String> removePlayerSection(List<String> lines, String groupMarker) {
        List<String> result = new ArrayList<>();
        boolean inTargetSection = false;
        boolean foundSection = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Check if this is the start of our target section
            if (line.equals(groupMarker)) {
                inTargetSection = true;
                foundSection = true;
                continue; // Skip this line
            }

            // Check if we've reached a new group (end of current section)
            if (inTargetSection && line.startsWith("group ")) {
                inTargetSection = false;
                // Don't skip this line, it's the start of a different section
            }

            // If we're not in the target section, keep the line
            if (!inTargetSection) {
                result.add(lines.get(i));
            }
        }

        // Remove trailing empty lines if we removed a section
        if (foundSection) {
            while (!result.isEmpty() && result.get(result.size() - 1).trim().isEmpty()) {
                result.remove(result.size() - 1);
            }
        }

        return result;
    }

    /**
     * Reloads the ChatControl plugin
     * Security: Uses Bukkit's command dispatcher to prevent command injection
     */
    private boolean reloadChatControl(Player player) {
        try {
            // Run the command synchronously on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Security: Use dispatchCommand which properly handles the command
                    // The command is hardcoded and not user-controlled
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chc reload");

                    if (success) {
                        player.sendMessage(ChatControlMessageManager.getInstance()
                                .getMessage("success"));
                        PrettyLogger.info("Reloaded ChatControl for player: " + player.getName());
                    } else {
                        player.sendMessage(ChatControlMessageManager.getInstance()
                                .getMessage("reload-failed"));
                        PrettyLogger.warn("Failed to reload ChatControl");
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatControlMessageManager.getInstance()
                            .getMessage("reload-error"));
                    PrettyLogger.warn("Error reloading ChatControl: " + e.getMessage());
                }
            });

            return true;

        } catch (Exception e) {
            PrettyLogger.warn("Error scheduling ChatControl reload: " + e.getMessage());
            return false;
        }
    }
}
