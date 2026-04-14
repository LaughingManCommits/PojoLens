package laughing.man.commits.benchmark;

import laughing.man.commits.filter.Filter;
import laughing.man.commits.filter.FilterImpl;

import java.lang.reflect.Field;

final class BenchmarkFilterCacheReset {

    private static final Field FAST_ARRAY_STATE = field("fastArrayState");
    private static final Field FAST_STATS_STATE = field("fastStatsState");
    private static final Field SOURCE_INDEX_CACHE = field("sourceIndexCache");

    private BenchmarkFilterCacheReset() {
    }

    static void clear(Filter filter) {
        if (!(filter instanceof FilterImpl filterImpl)) {
            return;
        }
        clearField(FAST_ARRAY_STATE, filterImpl);
        clearField(FAST_STATS_STATE, filterImpl);
        clearField(SOURCE_INDEX_CACHE, filterImpl);
    }

    private static Field field(String name) {
        try {
            Field field = FilterImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing benchmark cache field '" + name + "'", e);
        }
    }

    private static void clearField(Field field, FilterImpl filter) {
        try {
            field.set(filter, null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to clear benchmark cache field '" + field.getName() + "'", e);
        }
    }
}
