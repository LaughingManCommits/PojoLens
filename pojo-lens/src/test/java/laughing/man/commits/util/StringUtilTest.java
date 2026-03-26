package laughing.man.commits.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilTest {

    @Test
    void isNullOrBlankReturnsTrueForNull() {
        assertTrue(StringUtil.isNullOrBlank(null));
    }

    @Test
    void isNullOrBlankReturnsTrueForEmptyString() {
        assertTrue(StringUtil.isNullOrBlank(""));
    }

    @Test
    void isNullOrBlankReturnsTrueForBlankString() {
        assertTrue(StringUtil.isNullOrBlank("   "));
        assertTrue(StringUtil.isNullOrBlank("\t"));
        assertTrue(StringUtil.isNullOrBlank("\n"));
        assertTrue(StringUtil.isNullOrBlank(" \t\n "));
    }

    @Test
    void isNullOrBlankReturnsFalseForNonBlankString() {
        assertFalse(StringUtil.isNullOrBlank("a"));
        assertFalse(StringUtil.isNullOrBlank("hello"));
        assertFalse(StringUtil.isNullOrBlank(" text "));
        assertFalse(StringUtil.isNullOrBlank("  value  "));
    }

    @Test
    void isNullReturnsTrueForNull() {
        assertTrue(StringUtil.isNull(null));
    }

    @Test
    void isNullReturnsTrueForEmptyString() {
        assertTrue(StringUtil.isNull(""));
    }

    @Test
    void isNullReturnsFalseForNonEmptyString() {
        assertFalse(StringUtil.isNull("a"));
        assertFalse(StringUtil.isNull("  "));
        assertFalse(StringUtil.isNull("hello"));
    }

    @Test
    void parseBoolStrictReturnsNullForNull() {
        assertNull(StringUtil.parseBoolStrict(null));
    }

    @Test
    void parseBoolStrictReturnsTrueForTruthyValues() {
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("true"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("TRUE"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("1"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("yes"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("YES"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("on"));
        assertEquals(Boolean.TRUE, StringUtil.parseBoolStrict("ON"));
    }

    @Test
    void parseBoolStrictReturnsFalseForFalsyValues() {
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("false"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("FALSE"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("0"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("no"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("NO"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("off"));
        assertEquals(Boolean.FALSE, StringUtil.parseBoolStrict("OFF"));
    }

    @Test
    void parseBoolStrictReturnsNullForInvalidValues() {
        assertNull(StringUtil.parseBoolStrict("maybe"));
        assertNull(StringUtil.parseBoolStrict("2"));
        assertNull(StringUtil.parseBoolStrict(""));
    }

    @Test
    void isNumberReturnsTrueForValidNumbers() {
        assertTrue(StringUtil.isNumber("123"));
        assertTrue(StringUtil.isNumber("123.45"));
        assertTrue(StringUtil.isNumber("-456"));
        assertTrue(StringUtil.isNumber("0"));
    }

    @Test
    void isNumberReturnsFalseForInvalidNumbers() {
        assertFalse(StringUtil.isNumber("abc"));
        assertFalse(StringUtil.isNumber("123abc"));
    }
}
