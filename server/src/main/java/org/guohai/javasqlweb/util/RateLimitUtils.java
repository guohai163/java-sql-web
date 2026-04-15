package org.guohai.javasqlweb.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small in-memory rate limiter for sensitive endpoints.
 */
public final class RateLimitUtils {

    private static final Map<String, CounterWindow> WINDOWS = new ConcurrentHashMap<>();

    private RateLimitUtils() {
    }

    public static boolean tryAcquire(String bucket, String key, int maxAttempts, long windowMillis) {
        String normalizedKey = bucket + ":" + (key == null || key.trim().isEmpty() ? "anonymous" : key.trim());
        CounterWindow counterWindow = WINDOWS.computeIfAbsent(normalizedKey, ignored -> new CounterWindow());
        long now = System.currentTimeMillis();
        synchronized (counterWindow) {
            if (now - counterWindow.windowStart >= windowMillis) {
                counterWindow.windowStart = now;
                counterWindow.counter = 0;
            }
            counterWindow.counter++;
            return counterWindow.counter <= maxAttempts;
        }
    }

    private static final class CounterWindow {
        private long windowStart = System.currentTimeMillis();
        private int counter;
    }
}
