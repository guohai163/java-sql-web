package org.guohai.javasqlweb.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 用户安全任务工具
 */
public final class UserSecurityTaskUtils {

    private UserSecurityTaskUtils() {
    }

    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    public static String hashUuid(String uuid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(uuid.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashByte : hashBytes) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成安全任务哈希", e);
        }
    }
}
