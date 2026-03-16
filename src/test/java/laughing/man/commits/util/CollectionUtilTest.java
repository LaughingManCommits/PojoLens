package laughing.man.commits.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionUtilTest {

    @Test
    void firstNonNullShouldReturnFirstPresentValue() {
        assertEquals("value", CollectionUtil.firstNonNull(Arrays.asList(null, "value", "later")));
    }

    @Test
    void firstNonNullShouldReturnNullWhenMissing() {
        assertNull(CollectionUtil.firstNonNull(Arrays.asList(null, null)));
        assertNull(CollectionUtil.firstNonNull(null));
    }

    @Test
    void applyLimitShouldReturnOriginalListWhenNoLimitApplies() {
        List<String> rows = List.of("a", "b");

        assertSame(rows, CollectionUtil.applyLimit(rows, null));
        assertSame(rows, CollectionUtil.applyLimit(rows, 2));
        assertSame(rows, CollectionUtil.applyLimit(rows, 3));
    }

    @Test
    void applyLimitShouldReturnBoundedCopyWhenLimitApplies() {
        ArrayList<String> rows = new ArrayList<>(List.of("a", "b", "c"));

        List<String> limited = CollectionUtil.applyLimit(rows, 2);

        assertEquals(List.of("a", "b"), limited);
        rows.set(0, "changed");
        assertEquals(List.of("a", "b"), limited);
    }

    @Test
    void applyLimitShouldReturnEmptyListForNonPositiveLimit() {
        assertTrue(CollectionUtil.applyLimit(List.of("a", "b"), 0).isEmpty());
        assertTrue(CollectionUtil.applyLimit(List.of("a", "b"), -1).isEmpty());
    }

    @Test
    void expectedMapCapacityShouldMatchExistingSizingRule() {
        assertEquals(16, CollectionUtil.expectedMapCapacity(0));
        assertEquals(16, CollectionUtil.expectedMapCapacity(-1));
        assertEquals(2, CollectionUtil.expectedMapCapacity(1));
        assertEquals(14, CollectionUtil.expectedMapCapacity(10));
    }
}
