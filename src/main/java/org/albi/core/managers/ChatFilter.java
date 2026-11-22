package org.albi.core.managers;

import org.albi.core.Core;
import org.albi.core.data.StatManagement;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

public final class ChatFilter {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern REPEATED_CHARS = Pattern.compile("(.)\\1{2,}");

    private final Core plugin;

    public ChatFilter(Core plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if a message should be blocked based on the configured bad_words list.
     * @param message The raw message to check.
     * @return true if the message contains blocked content.
     */
    public boolean isBlocked(String message) {
        if (message == null || message.isBlank()) return false;
        List<String> bannedWords = plugin.getConfig().getStringList("bad_words");
        if (bannedWords == null || bannedWords.isEmpty()) return false;
        return shouldBlock(message, bannedWords).blocked();
    }

    public boolean handleBlockedMessage(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String originalMessage = event.getMessage();

        if (originalMessage == null || originalMessage.isBlank()) {
            return false;
        }

        List<String> bannedWords = plugin.getConfig().getStringList("bad_words");
        if (bannedWords == null || bannedWords.isEmpty()) {
            return false;
        }

        FilterResult result = shouldBlock(originalMessage, bannedWords);
        if (!result.blocked()) {
            return false;
        }

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Your message has been blocked.");
        return true;
    }

    private FilterResult shouldBlock(String message, List<String> bannedWords) {
        String rawLower = message.toLowerCase(Locale.ROOT);
        String normalized = normalizeForComparison(message);

        for (String banned : bannedWords) {
            if (banned == null) continue;

            String trimmed = banned.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) continue;

            String normalizedBanned = normalizeForComparison(trimmed);
            if (normalizedBanned.isEmpty()) continue;

            // Exact raw contains
            if (rawLower.contains(trimmed)) {
                return new FilterResult(true, trimmed, "raw_contains");
            }

            // Normalized contains, catches spacing, symbols, repeated chars, leetspeak, accents
            if (normalized.contains(normalizedBanned)) {
                return new FilterResult(true, trimmed, "normalized_contains");
            }

            // Token-based fuzzy check for small edits
            if (normalizedBanned.length() >= 5 && containsNearMatch(normalized, normalizedBanned, 1)) {
                return new FilterResult(true, trimmed, "near_match");
            }
        }

        return new FilterResult(false, null, null);
    }

    private String normalizeForComparison(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String s = input.toLowerCase(Locale.ROOT);

        // Normalize unicode, strip accents
        s = Normalizer.normalize(s, Normalizer.Form.NFKD);
        s = COMBINING_MARKS.matcher(s).replaceAll("");
        s = ZERO_WIDTH.matcher(s).replaceAll("");

        // Broad character folding
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(foldChar(c));
        }

        s = sb.toString();

        // Remove all separators and junk
        s = NON_ALNUM.matcher(s).replaceAll("");

        // Collapse long repeated runs, ex: loooool -> lool
        s = REPEATED_CHARS.matcher(s).replaceAll("$1$1");

        return s;
    }

    private String foldChar(char c) {
        return switch (c) {
            // common generic folds
            case '@' -> "a";
            case '4' -> "a";
            case '8' -> "b";
            case '(' , '<' , '{' , '[' -> "c";
            case '3' -> "e";
            case '6' -> "g";
            case '9' -> "g";
            case '1', '!', '|', 'í', 'ì', 'î', 'ï', 'ı' -> "i";
            case '7' -> "t";
            case '0' -> "o";
            case '$', '5' -> "s";
            case '+' -> "t";
            case '2' -> "z";
            case '€' -> "e";
            case '£' -> "l";
            case '¥' -> "y";
            case '¢' -> "c";
            case '§' -> "s";

            // some multi-char folds people use generically
            case 'ß' -> "ss";
            default -> String.valueOf(c);
        };
    }

    private boolean containsNearMatch(String normalizedMessage, String normalizedBanned, int maxDistance) {
        int wordLen = normalizedBanned.length();
        if (wordLen == 0 || normalizedMessage.length() < Math.max(1, wordLen - maxDistance)) {
            return false;
        }

        int min = Math.max(1, wordLen - maxDistance);
        int max = Math.min(normalizedMessage.length(), wordLen + maxDistance);

        for (int size = min; size <= max; size++) {
            for (int start = 0; start + size <= normalizedMessage.length(); start++) {
                String window = normalizedMessage.substring(start, start + size);
                if (levenshtein(window, normalizedBanned, maxDistance) <= maxDistance) {
                    return true;
                }
            }
        }

        return false;
    }

    private int levenshtein(String a, String b, int limit) {
        int aLen = a.length();
        int bLen = b.length();

        if (Math.abs(aLen - bLen) > limit) {
            return limit + 1;
        }

        int[] prev = new int[bLen + 1];
        int[] curr = new int[bLen + 1];

        for (int j = 0; j <= bLen; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= aLen; i++) {
            curr[0] = i;
            int rowMin = curr[0];

            for (int j = 1; j <= bLen; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );

                rowMin = Math.min(rowMin, curr[j]);
            }

            if (rowMin > limit) {
                return limit + 1;
            }

            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[bLen];
    }

    private String safeJson(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record FilterResult(boolean blocked, String matchedWord, String reason) {}
}