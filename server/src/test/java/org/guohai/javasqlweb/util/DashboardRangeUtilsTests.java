package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardRangeUtilsTests {

    @Test
    void keepsHourGrainFor24HourRangeByDefault() {
        DashboardRangeUtils.DashboardRange range = DashboardRangeUtils.resolve("24h", "hour");

        assertEquals("24h", range.getRange());
        assertEquals("hour", range.getGrain());
        assertTrue(range.getEndTime().after(range.getStartTime()));
    }

    @Test
    void normalizesLongerRangeToDayGrain() {
        DashboardRangeUtils.DashboardRange range = DashboardRangeUtils.resolve("7d", "hour");

        assertEquals("7d", range.getRange());
        assertEquals("day", range.getGrain());
    }
}
