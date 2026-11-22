package org.albi.core.util;

import java.util.concurrent.ThreadLocalRandom;

public final class ShortIDUtils {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * Generate a random alphanumeric ID of a specific length.
     * @param length The length of the ID.
     * @return A random uppercase alphanumeric string.
     */
    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(ThreadLocalRandom.current().nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
