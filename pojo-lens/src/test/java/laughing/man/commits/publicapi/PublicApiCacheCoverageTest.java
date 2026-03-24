package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLens;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.PublicApiModels.StatsRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublicApiCacheCoverageTest extends AbstractPublicApiCoverageTest {

    @Test
    public void pojoLensCacheControlMethodsShouldReflectConfiguredState() {
        PojoLens.setSqlLikeCacheEnabled(false);
        assertFalse(PojoLens.isSqlLikeCacheEnabled());

        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheMaxEntries(2);
        assertEquals(2, PojoLens.getSqlLikeCacheMaxEntries());
        PojoLens.setSqlLikeCacheMaxWeight(0L);
        assertEquals(0L, PojoLens.getSqlLikeCacheMaxWeight());
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);
        assertEquals(0L, PojoLens.getSqlLikeCacheExpireAfterWriteMillis());
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        assertTrue(PojoLens.isSqlLikeCacheStatsEnabled());

        PojoLens.parse("where stringField = 'a'");
        PojoLens.parse("where stringField = 'b'");
        assertEquals(2, PojoLens.getSqlLikeCacheSize());
        assertEquals(2L, PojoLens.getSqlLikeCacheMisses());
        assertEquals(0L, PojoLens.getSqlLikeCacheHits());

        PojoLens.parse("where stringField = 'a'");
        assertEquals(1L, PojoLens.getSqlLikeCacheHits());
        assertEquals(0L, PojoLens.getSqlLikeCacheEvictions());
        assertEquals(PojoLens.getSqlLikeCacheSize(),
                ((Number) PojoLens.getSqlLikeCacheSnapshot().get("size")).intValue());

        PojoLens.resetSqlLikeCacheStats();
        assertEquals(0L, PojoLens.getSqlLikeCacheHits());
        assertEquals(0L, PojoLens.getSqlLikeCacheMisses());
        assertEquals(0L, PojoLens.getSqlLikeCacheEvictions());

        PojoLens.clearSqlLikeCache();
        assertEquals(0, PojoLens.getSqlLikeCacheSize());
    }

    @Test
    public void statsPlanCacheControlMethodsShouldReflectConfiguredState() {
        PojoLens.setStatsPlanCacheEnabled(true);
        PojoLens.setStatsPlanCacheMaxEntries(32);
        PojoLens.setStatsPlanCacheMaxWeight(0L);
        PojoLens.setStatsPlanCacheExpireAfterWriteMillis(0L);
        PojoLens.setStatsPlanCacheStatsEnabled(true);
        PojoLens.clearStatsPlanCache();
        PojoLens.resetStatsPlanCacheStats();

        List<Employee> employees = sampleEmployees();
        QueryBuilder stats = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addCount("total");

        stats.initFilter().filter(StatsRow.class);
        stats.initFilter().filter(StatsRow.class);

        assertTrue(PojoLens.getStatsPlanCacheMisses() >= 1L);
        assertTrue(PojoLens.getStatsPlanCacheHits() >= 1L);
        assertTrue(PojoLens.getStatsPlanCacheSize() >= 1);
        assertEquals(PojoLens.getStatsPlanCacheSize(),
                ((Number) PojoLens.getStatsPlanCacheSnapshot().get("size")).intValue());
        assertTrue(PojoLens.getStatsPlanCacheEvictions() >= 0L);

        PojoLens.setStatsPlanCacheEnabled(false);
        assertFalse(PojoLens.isStatsPlanCacheEnabled());
        PojoLens.setStatsPlanCacheEnabled(true);
        assertTrue(PojoLens.isStatsPlanCacheEnabled());
        assertTrue(PojoLens.isStatsPlanCacheStatsEnabled());
        assertEquals(0L, PojoLens.getStatsPlanCacheMaxWeight());
        assertEquals(0L, PojoLens.getStatsPlanCacheExpireAfterWriteMillis());
    }
}
