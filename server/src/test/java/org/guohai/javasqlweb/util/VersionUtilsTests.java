package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionUtilsTests {

    @Test
    void shouldStripLeadingVPrefix() {
        assertEquals("2.7.0", VersionUtils.normalize("v2.7.0"));
        assertEquals("2.7.0", VersionUtils.normalize("V2.7.0"));
    }

    @Test
    void shouldKeepPlainVersionUntouched() {
        assertEquals("2.7.0", VersionUtils.normalize("2.7.0"));
    }
}
