package net.mysterria.stuff.domain.joinmessage.service;

import net.mysterria.stuff.domain.joinmessage.model.LegacyMessages;
import net.mysterria.stuff.util.log.PrettyLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegacyJoinMessageImporter {

    private static final Pattern REQUIRE_SENDER = Pattern.compile("require sender script\\s+\"\\{player}\"\\s*==\\s*\"([^\"]+)\"");
    private static final List<String> JOIN_SUFFIXES = List.of("-join-message");
    private static final List<String> QUIT_SUFFIXES = List.of("-quit-message", "-leave-message");

    public LegacyMessages read(File joinFile, File quitFile) {
        LegacyMessages messages = new LegacyMessages();
        if (joinFile.exists()) {
            readFile(joinFile, JOIN_SUFFIXES, messages.getJoinByName(), messages::setDefaultJoin, messages::setFirstJoin);
        }
        if (quitFile.exists()) {
            readFile(quitFile, QUIT_SUFFIXES, messages.getQuitByName(), messages::setDefaultQuit, null);
        }
        return messages;
    }

    private void readFile(File file, List<String> suffixes, Map<String, String> byName,
                          Consumer<String> defaultMessage, Consumer<String> firstJoinMessage) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            PrettyLogger.warn("Could not read legacy messages from " + file.getName() + ": " + e.getMessage());
            return;
        }

        Consumer<String> target = null;
        String playerName = null;
        boolean inMessageBlock = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("group ")) {
                String group = line.substring("group ".length());
                playerName = stripSuffix(group, suffixes);
                if (group.equals("default")) {
                    target = defaultMessage;
                } else if (group.equals("firstjoinmessage") && firstJoinMessage != null) {
                    target = firstJoinMessage;
                } else {
                    target = null;
                }
                inMessageBlock = false;
            } else if (playerName != null && line.startsWith("require sender script")) {
                Matcher matcher = REQUIRE_SENDER.matcher(line);
                if (matcher.find()) {
                    playerName = matcher.group(1);
                }
            } else if (line.equals("message:") && (target != null || playerName != null)) {
                inMessageBlock = true;
            } else if (inMessageBlock && line.startsWith("- ")) {
                String message = line.substring(2);
                if (target != null) {
                    target.accept(message);
                } else {
                    byName.putIfAbsent(playerName, message);
                }
                inMessageBlock = false;
            }
        }
    }

    private String stripSuffix(String group, List<String> suffixes) {
        for (String suffix : suffixes) {
            if (group.endsWith(suffix)) {
                return group.substring(0, group.length() - suffix.length());
            }
        }
        return null;
    }
}
