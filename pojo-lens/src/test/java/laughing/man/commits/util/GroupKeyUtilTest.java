package laughing.man.commits.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupKeyUtilTest {

    @Test
    void nullValueShouldReturnNullGroupKey() {
        assertEquals(GroupKeyUtil.NULL_GROUP_KEY, GroupKeyUtil.toGroupKeyValue(null, null));
    }

    @Test
    void emptyStringShouldReturnNullGroupKey() {
        assertEquals(GroupKeyUtil.NULL_GROUP_KEY, GroupKeyUtil.toGroupKeyValue("", null));
    }

    @Test
    void nonEmptyStringShouldReturnAsIs() {
        assertEquals("foo", GroupKeyUtil.toGroupKeyValue("foo", null));
    }

    @Test
    void numericValueShouldBeCastToString() {
        assertEquals("42", GroupKeyUtil.toGroupKeyValue(42L, null));
    }

    @Test
    void nullGroupKeyConstantShouldHaveExpectedValue() {
        assertEquals("<NULL>", GroupKeyUtil.NULL_GROUP_KEY);
    }
}
