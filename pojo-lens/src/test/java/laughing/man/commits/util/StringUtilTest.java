package laughing.man.commits.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {

    @Test
    void isNullOrBlank_shouldReturnTrueForNull() {
        assertTrue(StringUtil.isNullOrBlank(null));
    }

    @Test
    void isNullOrBlank_shouldReturnTrueForEmptyString() {
        assertTrue(StringUtil.isNullOrBlank(""));
    }

    @Test
    void isNullOrBlank_shouldReturnTrueForBlankString() {
        assertTrue(StringUtil.isNullOrBlank("   "));
        assertTrue(StringUtil.isNullOrBlank("\t"));
        assertTrue(StringUtil.isNullOrBlank("\n"));
        assertTrue(StringUtil.isNullOrBlank(" \t\n "));
    }

    @Test
    void isNullOrBlank_shouldReturnFalseForNonBlankString() {
        assertFalse(StringUtil.isNullOrBlank("a"));
        assertFalse(StringUtil.isNullOrBlank("hello"));
        assertFalse(StringUtil.isNullOrBlank(" text "));
        assertFalse(StringUtil.isNullOrBlank("  value  "));
    }

    @Test
    void isNull_shouldReturnTrueForNull() {
        assertTrue(StringUtil.isNull(null));
    }

    @Test
    void isNull_shouldReturnTrueForEmptyString() {
        assertTrue(StringUtil.isNull(""));
    }

    @Test
    void isNull_shouldReturnFalseForNonEmptyString() {
        assertFalse(StringUtil.isNull("a"));
        assertFalse(StringUtil.isNull("  "));
        assertFalse(StringUtil.isNull("hello"));
    }

    @Test
    void parseBoolStrict_shouldReturnNullForNull() {
        assertNull(StringUtil.parseBoolStrict(null));
    }

    @Test
    void parseBoolStrict_shouldReturnTrueForTruthyValues() {
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("true"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("TRUE"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("1"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("yes"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("YES"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("on"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("ON"));
    }

    @Test
    void parseBoolStrict_shouldReturnFalseForFalsyValues() {
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("false"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("FALSE"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("0"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("no"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("NO"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("off"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("OFF"));
    }

    @Test
    void parseBoolStrict_shouldReturnNullForInvalidValues() {
        assertNull(StringUtil.parseBoolStrict("maybe"));
        assertNull(StringUtil.parseBoolStrict("2"));
        assertNull(StringUtil.parseBoolStrict(""));
    }

    @Test
    void isNumber_shouldReturnTrueForValidNumbers() {
        assertTrue(StringUtil.isNumber("123"));
        assertTrue(StringUtil.isNumber("123.45"));
        assertTrue(StringUtil.isNumber("-456"));
        assertTrue(StringUtil.isNumber("0"));
    }

    @Test
    void isNumber_shouldReturnFalseForInvalidNumbers() {
        assertFalse(StringUtil.isNumber("abc"));
        assertFalse(StringUtil.isNumber("123abc"));
    }
}
