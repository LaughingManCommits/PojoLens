package laughing.man.commits.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReflectionUtilEnumFieldTest {

    @Test
    void collectQueryableFieldNamesShouldIncludeEnumLeaves() {
        assertEquals(
                List.of("status", "details.level"),
                ReflectionUtil.collectQueryableFieldNames(EnumRoot.class)
        );
    }

    @Test
    void collectQueryableFieldTypesShouldIncludeEnumLeaves() {
        assertEquals(
                Map.of("status", Status.class, "details.level", Level.class),
                ReflectionUtil.collectQueryableFieldTypes(EnumRoot.class)
        );
    }

    private static final class EnumRoot {
        private Status status;
        private Details details;
    }

    private static final class Details {
        private Level level;
    }

    private enum Status {
        ACTIVE,
        INACTIVE
    }

    private enum Level {
        JUNIOR,
        SENIOR
    }
}
