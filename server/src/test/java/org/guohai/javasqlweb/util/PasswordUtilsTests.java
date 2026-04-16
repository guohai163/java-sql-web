package org.guohai.javasqlweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilsTests {

    @Test
    void acceptsPasswordWithThreeCharacterCategories() {
        assertNull(PasswordUtils.validateComplexity("Abcdef12"));
        assertNull(PasswordUtils.validateComplexity("Abcdef!@"));
    }

    @Test
    void rejectsPasswordWhenItDoesNotMeetComplexityRule() {
        assertEquals("密码至少需要8位", PasswordUtils.validateComplexity("Ab1!"));
        assertEquals("密码至少需要包含大写字母、小写字母、数字、特殊字符中的3类",
                PasswordUtils.validateComplexity("abcdefgh"));
    }
}
