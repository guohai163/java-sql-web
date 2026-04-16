package org.guohai.javasqlweb.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;

/**
 * Password helpers.
 */
public final class PasswordUtils {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TEMP_PASSWORD_LENGTH = 24;
    private static final int MIN_COMPLEX_PASSWORD_LENGTH = 8;

    private PasswordUtils() {
    }

    public static String encode(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    public static boolean isBcryptHash(String passwordHash) {
        return passwordHash != null && passwordHash.startsWith("$2");
    }

    public static String legacyHash(String rawPassword) {
        return Utils.MD5(Utils.MD5(rawPassword) + "jsa");
    }

    public static String randomTemporaryPassword() {
        StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            builder.append(RANDOM_CHARS.charAt(SECURE_RANDOM.nextInt(RANDOM_CHARS.length())));
        }
        return builder.toString();
    }

    public static String validateComplexity(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            return "密码不能为空";
        }
        if (rawPassword.length() < MIN_COMPLEX_PASSWORD_LENGTH) {
            return "密码至少需要8位";
        }
        int categoryCount = 0;
        if (rawPassword.chars().anyMatch(Character::isUpperCase)) {
            categoryCount++;
        }
        if (rawPassword.chars().anyMatch(Character::isLowerCase)) {
            categoryCount++;
        }
        if (rawPassword.chars().anyMatch(Character::isDigit)) {
            categoryCount++;
        }
        if (rawPassword.chars().anyMatch(value -> !Character.isLetterOrDigit(value))) {
            categoryCount++;
        }
        if (categoryCount < 3) {
            return "密码至少需要包含大写字母、小写字母、数字、特殊字符中的3类";
        }
        return null;
    }
}
