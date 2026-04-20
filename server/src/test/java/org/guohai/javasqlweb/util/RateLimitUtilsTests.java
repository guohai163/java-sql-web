package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitUtilsTests {

    @AfterEach
    void tearDown() {
        RateLimitUtils.clearAll();
    }

    @Test
    void cleanupExpiredWindowsRemovesInactiveEntries() throws Exception {
        assertTrue(RateLimitUtils.tryAcquire("login", "alice", 10, 5));
        assertEquals(1, RateLimitUtils.windowCount());

        Thread.sleep(20L);
        RateLimitUtils.cleanupExpiredWindows(System.currentTimeMillis());

        assertEquals(0, RateLimitUtils.windowCount());
    }

    @Test
    void cleanupExpiredWindowsKeepsActiveEntries() {
        assertTrue(RateLimitUtils.tryAcquire("login", "alice", 10, 1000));

        RateLimitUtils.cleanupExpiredWindows(System.currentTimeMillis());

        assertEquals(1, RateLimitUtils.windowCount());
    }
}
