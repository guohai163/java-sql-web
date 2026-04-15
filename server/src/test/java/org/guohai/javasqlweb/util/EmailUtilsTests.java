package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailUtilsTests {

    @Test
    void validatesAndNormalizesEmail() {
        assertTrue(EmailUtils.isValid("User.Name+tag@example.com"));
        assertEquals("user.name+tag@example.com", EmailUtils.normalize(" User.Name+tag@example.com "));
    }

    @Test
    void extractsUserNameFromEmailPrefix() {
        assertEquals("alice", EmailUtils.extractUserName("Alice@example.com"));
        assertNull(EmailUtils.extractUserName("invalid-email"));
    }
}
