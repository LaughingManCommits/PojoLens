package laughing.man.commits.util;

import laughing.man.commits.enums.Clauses;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectUtilTest {

    @Test
    void compareObjectShouldSupportSetForPositiveMatch() {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add("x");
        candidates.add("y");
        candidates.add("z");

        assertTrue(ObjectUtil.compareObject("y", candidates, Clauses.EQUAL, null));
        assertFalse(ObjectUtil.compareObject("a", candidates, Clauses.EQUAL, null));
    }

    @Test
    void compareObjectShouldSupportSetForNegatedClauses() {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add("x");
        candidates.add("y");

        assertTrue(ObjectUtil.compareObject("a", candidates, Clauses.NOT_EQUAL, null));
        assertFalse(ObjectUtil.compareObject("x", candidates, Clauses.NOT_EQUAL, null));
    }

    @Test
    void compareObjectShouldSupportHashMapValues() {
        Map<Integer, String> candidates = new HashMap<>();
        candidates.put(1, "north");
        candidates.put(2, "south");

        assertTrue(ObjectUtil.compareObject("south", candidates, Clauses.EQUAL, null));
        assertFalse(ObjectUtil.compareObject("west", candidates, Clauses.EQUAL, null));
    }

    @Test
    void compareObjectShouldStillSupportPrimitiveArrays() {
        int[] candidates = new int[]{1, 2, 3};

        assertTrue(ObjectUtil.compareObject(2, candidates, Clauses.EQUAL, null));
        assertFalse(ObjectUtil.compareObject(4, candidates, Clauses.EQUAL, null));
    }

    @Test
    void compareObjectShouldSupportGenericIterable() {
        Iterable<String> values = () -> List.of("alpha", "beta").iterator();

        assertTrue(ObjectUtil.compareObject("beta", values, Clauses.EQUAL, null));
        assertFalse(ObjectUtil.compareObject("gamma", values, Clauses.EQUAL, null));
    }

    @Test
    void compareObjectContainsShouldBeNullSafe() {
        assertFalse(ObjectUtil.compareObject("alpha", java.util.Arrays.asList((String) null), Clauses.CONTAINS, null));
    }

    @Test
    void castValueShouldHandleNullTargetType() {
        assertNull(ObjectUtil.castValue("123", null));
    }

    @Test
    void castValueShouldReturnInputWhenAlreadyRequestedType() {
        Date now = new Date();
        assertSame(now, ObjectUtil.castValue(now, Date.class));
    }
}

