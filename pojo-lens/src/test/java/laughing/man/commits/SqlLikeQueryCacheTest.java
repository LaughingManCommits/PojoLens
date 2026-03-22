package laughing.man.commits;

import laughing.man.commits.sqllike.SqlLikeQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikeQueryCacheTest {

    @BeforeEach
    public void setUp() {
        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        PojoLens.setSqlLikeCacheMaxEntries(256);
        PojoLens.setSqlLikeCacheMaxWeight(0L);
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();
    }

    @AfterEach
    public void tearDown() {
        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        PojoLens.setSqlLikeCacheMaxEntries(256);
        PojoLens.setSqlLikeCacheMaxWeight(0L);
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();
    }

    @Test
    public void parseShouldHitCacheForRepeatedNormalizedQuery() {
        SqlLikeQuery first = PojoLens.parse(" where stringField = 'abc' ");
        SqlLikeQuery second = PojoLens.parse("where stringField = 'abc'");

        assertSame(first, second);
        assertEquals(1L, PojoLens.getSqlLikeCacheHits());
        assertEquals(1L, PojoLens.getSqlLikeCacheMisses());
        assertEquals(1, PojoLens.getSqlLikeCacheSize());
    }

    @Test
    public void parseShouldBypassCacheWhenDisabled() {
        PojoLens.setSqlLikeCacheEnabled(false);

        SqlLikeQuery first = PojoLens.parse("where stringField = 'abc'");
        SqlLikeQuery second = PojoLens.parse("where stringField = 'abc'");

        assertNotSame(first, second);
        assertEquals(0L, PojoLens.getSqlLikeCacheHits());
        assertEquals(2L, PojoLens.getSqlLikeCacheMisses());
        assertEquals(0, PojoLens.getSqlLikeCacheSize());
    }

    @Test
    public void cacheShouldEvictWhenMaxEntriesExceeded() {
        PojoLens.setSqlLikeCacheMaxEntries(1);

        PojoLens.parse("where stringField = 'a'");
        PojoLens.parse("where stringField = 'b'");
        PojoLens.parse("where stringField = 'a'");

        assertEquals(3L, PojoLens.getSqlLikeCacheHits() + PojoLens.getSqlLikeCacheMisses());
        assertTrue(PojoLens.getSqlLikeCacheSize() <= PojoLens.getSqlLikeCacheMaxEntries());
    }

    @Test
    public void cacheSnapshotShouldExposeCountersAndLimits() {
        PojoLens.parse("where stringField = 'a'");
        PojoLens.parse("where stringField = 'a'");

        Map<String, Object> snapshot = PojoLens.getSqlLikeCacheSnapshot();
        assertEquals(Boolean.TRUE, snapshot.get("enabled"));
        assertEquals(PojoLens.getSqlLikeCacheMaxEntries(), ((Number) snapshot.get("maxEntries")).intValue());
        assertEquals(PojoLens.getSqlLikeCacheSize(), ((Number) snapshot.get("size")).intValue());
        assertEquals(PojoLens.getSqlLikeCacheHits(), ((Number) snapshot.get("hits")).longValue());
        assertEquals(PojoLens.getSqlLikeCacheMisses(), ((Number) snapshot.get("misses")).longValue());
        assertEquals(PojoLens.getSqlLikeCacheEvictions(), ((Number) snapshot.get("evictions")).longValue());
    }
}

