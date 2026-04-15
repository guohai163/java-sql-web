package org.guohai.javasqlweb.util;

import org.guohai.javasqlweb.beans.UserBean;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Access token helpers.
 */
public final class AccessTokenUtils {

    public static final String ACCESS_TOKEN_PREFIX = "jsw_";
    public static final String STATUS_NOT_CREATED = "NOT_CREATED";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    private static final int ACCESS_TOKEN_BYTES = 20;
    private static final int MASK_VISIBLE_CHARS = 4;
    private static final long ACCESS_TOKEN_VALID_DAYS = 90L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AccessTokenUtils() {
    }

    public static String generateAccessToken() {
        byte[] bytes = new byte[ACCESS_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(ACCESS_TOKEN_PREFIX);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    public static Date buildExpireTime() {
        return Date.from(Instant.now().plus(ACCESS_TOKEN_VALID_DAYS, ChronoUnit.DAYS));
    }

    public static boolean hasAccessToken(UserBean user) {
        return user != null
                && user.getAccessToken() != null
                && !user.getAccessToken().trim().isEmpty();
    }

    public static boolean isExpired(Date expireTime) {
        return expireTime == null || !expireTime.after(new Date());
    }

    public static String resolveStatus(UserBean user) {
        if (!hasAccessToken(user)) {
            return STATUS_NOT_CREATED;
        }
        return isExpired(user.getAccessTokenExpireTime()) ? STATUS_EXPIRED : STATUS_ACTIVE;
    }

    public static String maskAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            return "";
        }
        if (accessToken.length() <= MASK_VISIBLE_CHARS * 2) {
            return "****";
        }
        return accessToken.substring(0, MASK_VISIBLE_CHARS)
                + "************************"
                + accessToken.substring(accessToken.length() - MASK_VISIBLE_CHARS);
    }
}
