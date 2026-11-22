package org.albi.core.gui;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextTemplates {
    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    private TextTemplates() {}

    public static String format(String template, Object... values) {
        return TOKEN.matcher(template).replaceAll(match -> {
            String key = match.group(1);
            int index = key.matches("[1-9]\\d{0,3}") ? Integer.parseInt(key) - 1 : -1;
            return index >= 0 && index < values.length
                    ? Matcher.quoteReplacement(String.valueOf(values[index]))
                    : Matcher.quoteReplacement(match.group());
        });
    }

    public static String format(String template, Map<String, ?> values) {
        return TOKEN.matcher(template).replaceAll(match -> Matcher.quoteReplacement(values.containsKey(match.group(1))
                ? String.valueOf(values.get(match.group(1))) : match.group()));
    }
}
