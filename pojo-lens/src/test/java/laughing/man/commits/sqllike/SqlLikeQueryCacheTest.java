package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLens;
import laughing.man.commits.PojoLensRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikeQueryCacheTest {

    private PojoLensRuntime runtime;

    @BeforeEach
    public void setUp() {
        runtime = PojoLens.newRuntime();
        runtime.sqlLikeCache().setEnabled(true);
        runtime.sqlLikeCache().setStatsEnabled(true);
        runtime.sqlLikeCache().setMaxEntries(256);
        runtime.sqlLikeCache().setMaxWeight(0L);
        runtime.sqlLikeCache().setExpireAfterWriteMillis(0L);
        runtime.sqlLikeCache().clear();
        runtime.sqlLikeCache().resetStats();
    }

    @Test
    public void parseShouldHitCacheForRepeatedNormalizedQuery() {
        SqlLikeQuery first = runtime.parse(" where stringField = 'abc' ");
        SqlLikeQuery second = runtime.parse("where stringField = 'abc'");

        assertSame(first, second);
        assertEquals(1L, runtime.sqlLikeCache().getHits());
        assertEquals(1L, runtime.sqlLikeCache().getMisses());
        assertEquals(1, runtime.sqlLikeCache().getSize());
    }

    @Test
    public void parseShouldBypassCacheWhenDisabled() {
        runtime.sqlLikeCache().setEnabled(false);

        SqlLikeQuery first = runtime.parse("where stringField = 'abc'");
        SqlLikeQuery second = runtime.parse("where stringField = 'abc'");

        assertNotSame(first, second);
        assertEquals(0L, runtime.sqlLikeCache().getHits());
        assertEquals(2L, runtime.sqlLikeCache().getMisses());
        assertEquals(0, runtime.sqlLikeCache().getSize());
    }

    @Test
    public void cacheShouldEvictWhenMaxEntriesExceeded() {
        runtime.sqlLikeCache().setMaxEntries(1);

        runtime.parse("where stringField = 'a'");
        runtime.parse("where stringField = 'b'");
        runtime.parse("where stringField = 'a'");

        assertEquals(3L, runtime.sqlLikeCache().getHits() + runtime.sqlLikeCache().getMisses());
        assertTrue(runtime.sqlLikeCache().getSize() <= runtime.sqlLikeCache().getMaxEntries());
    }

    @Test
    public void cacheSnapshotShouldExposeCountersAndLimits() {
        runtime.parse("where stringField = 'a'");
        runtime.parse("where stringField = 'a'");

        Map<String, Object> snapshot = runtime.sqlLikeCache().snapshot();
        assertEquals(Boolean.TRUE, snapshot.get("enabled"));
        assertEquals(runtime.sqlLikeCache().getMaxEntries(), ((Number) snapshot.get("maxEntries")).intValue());
        assertEquals(runtime.sqlLikeCache().getSize(), ((Number) snapshot.get("size")).intValue());
        assertEquals(runtime.sqlLikeCache().getHits(), ((Number) snapshot.get("hits")).longValue());
        assertEquals(runtime.sqlLikeCache().getMisses(), ((Number) snapshot.get("misses")).longValue());
        assertEquals(runtime.sqlLikeCache().getEvictions(), ((Number) snapshot.get("evictions")).longValue());
    }
}
