package org.guohai.javasqlweb.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Very small in-memory rate limiter for sensitive endpoints.
 */
public final class RateLimitUtils {

    private static final Map<String, CounterWindow> WINDOWS = new ConcurrentHashMap<>();
    private static final AtomicInteger INVOCATION_COUNTER = new AtomicInteger();
    private static final int CLEANUP_INTERVAL = 128;

    private RateLimitUtils() {
    }

    public static boolean tryAcquire(String bucket, String key, int maxAttempts, long windowMillis) {
        String normalizedKey = bucket + ":" + (key == null || key.trim().isEmpty() ? "anonymous" : key.trim());
        long now = System.currentTimeMillis();
        CounterWindow counterWindow = WINDOWS.computeIfAbsent(normalizedKey, ignored -> new CounterWindow());
        boolean allowed;
        synchronized (counterWindow) {
            counterWindow.windowMillis = Math.max(1L, windowMillis);
            if (now - counterWindow.windowStart >= counterWindow.windowMillis) {
                counterWindow.windowStart = now;
                counterWindow.counter = 0;
            }
            counterWindow.lastAccessAt = now;
            counterWindow.counter++;
            allowed = counterWindow.counter <= maxAttempts;
        }
        cleanupIfNeeded(now);
        return allowed;
    }

    static void cleanupExpiredWindows(long now) {
        WINDOWS.forEach((key, counterWindow) -> {
            if (counterWindow != null && counterWindow.isExpired(now)) {
                WINDOWS.remove(key, counterWindow);
            }
        });
    }

    static void clearAll() {
        WINDOWS.clear();
        INVOCATION_COUNTER.set(0);
    }

    static int windowCount() {
        return WINDOWS.size();
    }

    private static void cleanupIfNeeded(long now) {
        if (INVOCATION_COUNTER.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanupExpiredWindows(now);
        }
    }

    private static final class CounterWindow {
        private long windowStart = System.currentTimeMillis();
        private long lastAccessAt = windowStart;
        private long windowMillis = 1L;
        private int counter;

        private boolean isExpired(long now) {
            return now - lastAccessAt >= windowMillis;
        }
    }
}
