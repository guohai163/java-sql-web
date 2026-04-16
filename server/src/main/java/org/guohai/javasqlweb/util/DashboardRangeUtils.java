package org.guohai.javasqlweb.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Dashboard range helpers.
 */
public final class DashboardRangeUtils {

    private DashboardRangeUtils() {
    }

    public static DashboardRange resolve(String range, String grain) {
        String normalizedRange = normalizeRange(range);
        String normalizedGrain = normalizeGrain(normalizedRange, grain);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start;
        switch (normalizedRange) {
            case "7d":
                start = end.minusDays(7);
                break;
            case "30d":
                start = end.minusDays(30);
                break;
            case "24h":
            default:
                start = end.minusHours(24);
                break;
        }
        return new DashboardRange(
                normalizedRange,
                normalizedGrain,
                Date.from(start.atZone(ZoneId.systemDefault()).toInstant()),
                Date.from(end.atZone(ZoneId.systemDefault()).toInstant())
        );
    }

    private static String normalizeRange(String range) {
        if ("7d".equalsIgnoreCase(range)) {
            return "7d";
        }
        if ("30d".equalsIgnoreCase(range)) {
            return "30d";
        }
        return "24h";
    }

    private static String normalizeGrain(String range, String grain) {
        if ("24h".equals(range)) {
            return "day".equalsIgnoreCase(grain) ? "day" : "hour";
        }
        return "day";
    }

    public static final class DashboardRange {
        private final String range;
        private final String grain;
        private final Date startTime;
        private final Date endTime;

        public DashboardRange(String range, String grain, Date startTime, Date endTime) {
            this.range = range;
            this.grain = grain;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getRange() {
            return range;
        }

        public String getGrain() {
            return grain;
        }

        public Date getStartTime() {
            return startTime;
        }

        public Date getEndTime() {
            return endTime;
        }
    }
}
