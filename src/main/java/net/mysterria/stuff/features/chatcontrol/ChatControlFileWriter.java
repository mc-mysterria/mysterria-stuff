package net.mysterria.stuff.features.chatcontrol;

import net.mysterria.stuff.MysterriaStuff;
import net.mysterria.stuff.utils.PrettyLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class ChatControlFileWriter {


    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_-]{1,16}$");

    private static final String CHATCONTROL_BASE = "plugins/ChatControl/messages";
    private final MysterriaStuff plugin;

    public ChatControlFileWriter(MysterriaStuff plugin) {
        this.plugin = plugin;
    }


    public boolean writePlayerMessages(Player player, String joinMessage, String quitMessage) {
        String username = player.getName();


        if (!isValidUsername(username)) {
            PrettyLogger.warn("Invalid username format: " + username);
            return false;
        }


        if (!joinMessage.contains("%player%")) {
            player.sendMessage(ChatControlMessageManager.getInstance().getMessage("join-missing-placeholder"));
            return false;
        }

        if (!quitMessage.contains("%player%")) {
            player.sendMessage(ChatControlMessageManager.getInstance().getMessage("quit-missing-placeholder"));
            return false;
        }


        joinMessage = sanitizeMessage(joinMessage);
        quitMessage = sanitizeMessage(quitMessage);

        try {

            File pluginsDir = plugin.getDataFolder().getParentFile();
            if (pluginsDir == null || !pluginsDir.exists()) {
                PrettyLogger.warn("Failed to locate plugins directory");
                player.sendMessage(ChatControlMessageManager.getInstance().getMessage("write-error"));
                return false;
            }


            Path joinPath = pluginsDir.toPath().resolve("ChatControl/messages/join.rs").normalize();
            Path quitPath = pluginsDir.toPath().resolve("ChatControl/messages/quit.rs").normalize();
            Path baseDir = pluginsDir.toPath().resolve("ChatControl/messages").normalize();


            if (!joinPath.startsWith(baseDir) || !quitPath.startsWith(baseDir)) {
                PrettyLogger.warn("Path traversal attempt detected for user: " + username);
                return false;
            }


            if (!Files.exists(baseDir)) {
                player.sendMessage(ChatControlMessageManager.getInstance().getMessage("chatcontrol-not-found"));
                PrettyLogger.warn("ChatControl directory not found at: " + baseDir);
                return false;
            }


            if (!writeOrUpdateMessage(joinPath.toFile(), username, joinMessage, "join")) {
                return false;
            }


            if (!writeOrUpdateMessage(quitPath.toFile(), username, quitMessage, "quit")) {
                return false;
            }


            return reloadChatControl(player);

        } catch (Exception e) {
            PrettyLogger.warn("Error writing ChatControl messages: " + e.getMessage());
            player.sendMessage(ChatControlMessageManager.getInstance().getMessage("write-error"));
            return false;
        }
    }


    private boolean isValidUsername(String username) {
        return VALID_USERNAME.matcher(username).matches();
    }


    private String sanitizeMessage(String message) {

        message = message.replace("\0", "");


        message = message.replace("\r", "");
        message = message.replace("\n", " ");


        message = message.replace("`", "");
        message = message.replace("$(", "");
        message = message.replace("${", "");

        return message;
    }


    private boolean writeOrUpdateMessage(File file, String username, String message, String type) {
        try {

            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }


            List<String> lines = new ArrayList<>();
            if (file.length() > 0) {
                lines = Files.readAllLines(file.toPath());
            }


            String groupMarker = "group " + username + "-" + type + "-message";
            lines = removePlayerSection(lines, groupMarker);


            int insertIndex = findInsertionPoint(lines);


            List<String> newSection = new ArrayList<>();
            if (insertIndex > 0 && insertIndex < lines.size() && !lines.get(insertIndex - 1).isEmpty()) {
                newSection.add("");
            }
            newSection.add(groupMarker);
            newSection.add("require sender script \"{player}\" == \"" + username + "\"");
            newSection.add("message:");
            newSection.add("- " + message.replace("%player%", "{player}"));
            newSection.add("");


            lines.addAll(insertIndex, newSection);


            Files.write(file.toPath(), lines);

            PrettyLogger.debug("Successfully wrote " + type + " message for " + username);
            return true;

        } catch (IOException e) {
            PrettyLogger.warn("Failed to write to " + file.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }


    private List<String> removePlayerSection(List<String> lines, String groupMarker) {
        List<String> result = new ArrayList<>();
        boolean inTargetSection = false;
        boolean foundSection = false;

        for (String s : lines) {
            String line = s.trim();


            if (line.equals(groupMarker)) {
                inTargetSection = true;
                foundSection = true;
                continue;
            }


            if (inTargetSection && line.startsWith("group ")) {
                inTargetSection = false;

            }


            if (!inTargetSection) {
                result.add(s);
            }
        }


        if (foundSection) {
            while (!result.isEmpty() && result.getLast().trim().isEmpty()) {
                result.removeLast();
            }
        }

        return result;
    }


    private int findInsertionPoint(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();


            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }


            return i;
        }


        return lines.size();
    }


    private boolean reloadChatControl(Player player) {
        try {

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {


                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chc reload");

                    if (success) {
                        player.sendMessage(ChatControlMessageManager.getInstance().getMessage("success"));
                        PrettyLogger.info("Reloaded ChatControl for player: " + player.getName());
                    } else {
                        player.sendMessage(ChatControlMessageManager.getInstance().getMessage("reload-failed"));
                        PrettyLogger.warn("Failed to reload ChatControl");
                    }
                } catch (Exception e) {
                    player.sendMessage(ChatControlMessageManager.getInstance().getMessage("reload-error"));
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
