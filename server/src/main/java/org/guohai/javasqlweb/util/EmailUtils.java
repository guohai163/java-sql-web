package org.guohai.javasqlweb.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 邮箱工具
 */
public final class EmailUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);

    private EmailUtils() {
    }

    public static boolean isValid(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String extractUserName(String email) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail == null) {
            return null;
        }
        int index = normalizedEmail.indexOf('@');
        if (index <= 0) {
            return null;
        }
        return normalizedEmail.substring(0, index);
    }
}
