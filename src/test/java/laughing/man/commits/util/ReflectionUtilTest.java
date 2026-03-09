package laughing.man.commits.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionUtilTest {

    @Test
    void newUuidShouldUseUnderscoresAndDigitFreeAlphabeticEncoding() {
        String id = ReflectionUtil.newUUID();

        assertEquals(36, id.length());
        assertEquals('_', id.charAt(8));
        assertEquals('_', id.charAt(13));
        assertEquals('_', id.charAt(18));
        assertEquals('_', id.charAt(23));
        assertFalse(id.contains("-"));
        assertTrue(id.chars().noneMatch(Character::isDigit));
    }

    @Test
    void newUuidShouldRemainUniqueAcrossCalls() {
        String first = ReflectionUtil.newUUID();
        String second = ReflectionUtil.newUUID();

        assertNotEquals(first, second);
    }
}
