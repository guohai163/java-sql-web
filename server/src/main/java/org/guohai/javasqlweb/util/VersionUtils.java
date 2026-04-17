package org.guohai.javasqlweb.util;

public final class VersionUtils {

    private VersionUtils() {
    }

    public static String normalize(String version) {
        if (version == null) {
            return "";
        }
        String normalized = version.trim();
        while (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }
}
