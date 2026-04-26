package laughing.man.commits.publicapi;

import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import org.junit.jupiter.api.Test;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublicApiCacheCoverageTest extends AbstractPublicApiCoverageTest {

    @Test
    public void runtimeSqlLikeCacheControlsShouldReflectConfiguredState() {
        runtime.sqlLikeCache().setEnabled(false);
        assertFalse(runtime.sqlLikeCache().isEnabled());

        runtime.sqlLikeCache().setEnabled(true);
        runtime.sqlLikeCache().setMaxEntries(2);
        assertEquals(2, runtime.sqlLikeCache().getMaxEntries());
        runtime.sqlLikeCache().setMaxWeight(0L);
        assertEquals(0L, runtime.sqlLikeCache().getMaxWeight());
        runtime.sqlLikeCache().setExpireAfterWriteMillis(0L);
        assertEquals(0L, runtime.sqlLikeCache().getExpireAfterWriteMillis());
        runtime.sqlLikeCache().setStatsEnabled(true);
        assertTrue(runtime.sqlLikeCache().isStatsEnabled());

        runtime.parse("where stringField = 'a'");
        runtime.parse("where stringField = 'b'");
        assertEquals(2, runtime.sqlLikeCache().getSize());
        assertEquals(2L, runtime.sqlLikeCache().getMisses());
        assertEquals(0L, runtime.sqlLikeCache().getHits());

        runtime.parse("where stringField = 'a'");
        assertEquals(1L, runtime.sqlLikeCache().getHits());
        assertEquals(0L, runtime.sqlLikeCache().getEvictions());
        assertEquals(runtime.sqlLikeCache().getSize(),
                ((Number) runtime.sqlLikeCache().snapshot().get("size")).intValue());

        runtime.sqlLikeCache().resetStats();
        assertEquals(0L, runtime.sqlLikeCache().getHits());
        assertEquals(0L, runtime.sqlLikeCache().getMisses());
        assertEquals(0L, runtime.sqlLikeCache().getEvictions());

        runtime.sqlLikeCache().clear();
        assertEquals(0, runtime.sqlLikeCache().getSize());
    }

    @Test
    public void runtimeStatsPlanCacheControlsShouldReflectConfiguredState() {
        runtime.statsPlanCache().setEnabled(true);
        runtime.statsPlanCache().setMaxEntries(32);
        runtime.statsPlanCache().setMaxWeight(0L);
        runtime.statsPlanCache().setExpireAfterWriteMillis(0L);
        runtime.statsPlanCache().setStatsEnabled(true);
        runtime.statsPlanCache().clear();
        runtime.statsPlanCache().resetStats();
        assertEquals(0L, runtime.statsPlanCache().misses());
        assertEquals(0L, runtime.statsPlanCache().hits());
        assertEquals(0, runtime.statsPlanCache().size());
        assertEquals(runtime.statsPlanCache().size(),
                ((Number) runtime.statsPlanCache().snapshot().get("size")).intValue());
        assertEquals(0L, runtime.statsPlanCache().evictions());

        runtime.statsPlanCache().setEnabled(false);
        assertFalse(runtime.statsPlanCache().isEnabled());
        runtime.statsPlanCache().setEnabled(true);
        assertTrue(runtime.statsPlanCache().isEnabled());
        assertTrue(runtime.statsPlanCache().isStatsEnabled());
        assertEquals(0L, runtime.statsPlanCache().maxWeight());
        assertEquals(0L, runtime.statsPlanCache().expireAfterWriteMillis());
    }

    @Test
    public void runtimeStatsPlanCacheResetShouldClearEntriesAndCounters() {
        runtime.parse("select department, count(*) as total group by department")
                .filter(sampleEmployees(), DepartmentCount.class);
        runtime.parse("select department, count(*) as total group by department")
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(1, runtime.statsPlanCache().size());
        assertEquals(1L, runtime.statsPlanCache().misses());
        assertEquals(1L, runtime.statsPlanCache().hits());

        runtime.statsPlanCache().resetStats();

        assertEquals(0, runtime.statsPlanCache().size());
        assertEquals(0L, runtime.statsPlanCache().misses());
        assertEquals(0L, runtime.statsPlanCache().hits());
        assertEquals(0L, runtime.statsPlanCache().evictions());
        assertEquals(0, ((Number) runtime.statsPlanCache().snapshot().get("size")).intValue());

        runtime.parse("select department, count(*) as total group by department")
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(1L, runtime.statsPlanCache().misses());
        assertEquals(0L, runtime.statsPlanCache().hits());
    }
}


