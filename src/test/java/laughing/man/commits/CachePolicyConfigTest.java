package laughing.man.commits;

import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CachePolicyConfigTest {

    @BeforeEach
    public void setUp() {
        resetSqlLikePolicyDefaults();
        resetStatsPlanPolicyDefaults();
    }

    @AfterEach
    public void tearDown() {
        resetSqlLikePolicyDefaults();
        resetStatsPlanPolicyDefaults();
    }

    @Test
    public void sqlLikeCacheShouldExpireEntriesWhenConfigured() throws Exception {
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(5L);
        PojoLens.setSqlLikeCacheMaxEntries(64);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();

        SqlLikeQuery first = PojoLens.parse("where stringField = 'policy'");
        Thread.sleep(30L);
        SqlLikeQuery second = PojoLens.parse("where stringField = 'policy'");

        assertNotSame(first, second);
        assertTrue(PojoLens.getSqlLikeCacheMisses() >= 2L);
        assertTrue(PojoLens.getSqlLikeCacheEvictions() >= 1L);
    }

    @Test
    public void sqlLikeCacheStatsToggleShouldSuppressCounters() {
        PojoLens.setSqlLikeCacheStatsEnabled(false);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();

        PojoLens.parse("where stringField = 'a'");
        PojoLens.parse("where stringField = 'a'");

        assertEquals(0L, PojoLens.getSqlLikeCacheHits());
        assertEquals(0L, PojoLens.getSqlLikeCacheMisses());
        assertEquals(0L, PojoLens.getSqlLikeCacheEvictions());
    }

    @Test
    public void statsPlanCacheShouldExpireEntriesWhenConfigured() throws Exception {
        PojoLens.setStatsPlanCacheExpireAfterWriteMillis(5L);
        PojoLens.setStatsPlanCacheMaxEntries(64);
        PojoLens.clearStatsPlanCache();
        PojoLens.resetStatsPlanCacheStats();

        runStatsPlanQuery(sampleEmployees());
        Thread.sleep(30L);
        runStatsPlanQuery(sampleEmployees());

        assertTrue(PojoLens.getStatsPlanCacheMisses() >= 2L);
        assertTrue(PojoLens.getStatsPlanCacheEvictions() >= 1L);
    }

    @Test
    public void statsPlanCacheStatsToggleShouldSuppressCounters() {
        List<Employee> employees = sampleEmployees();
        PojoLens.setStatsPlanCacheStatsEnabled(false);
        PojoLens.clearStatsPlanCache();
        PojoLens.resetStatsPlanCacheStats();

        runStatsPlanQuery(employees);
        runStatsPlanQuery(employees);

        assertEquals(0L, PojoLens.getStatsPlanCacheHits());
        assertEquals(0L, PojoLens.getStatsPlanCacheMisses());
        assertEquals(0L, PojoLens.getStatsPlanCacheEvictions());
    }

    @Test
    public void sqlLikeAliasedStatsQueryShouldReuseStatsPlanCache() {
        List<Employee> employees = sampleEmployees();
        SqlLikeQuery query = PojoLens.parse("select department as dept, count(*) as total group by department");

        query.filter(employees, DepartmentCountAlias.class);
        query.filter(employees, DepartmentCountAlias.class);

        assertEquals(1L, PojoLens.getStatsPlanCacheMisses());
        assertEquals(1L, PojoLens.getStatsPlanCacheHits());
    }

    @Test
    public void sqlLikeStatsChartShouldReuseStatsPlanCache() {
        List<Employee> employees = sampleEmployees();
        SqlLikeQuery query = PojoLens.parse("select department, count(*) as total group by department");
        ChartSpec spec = ChartSpec.of(ChartType.BAR, "department", "total");

        query.chart(employees, DepartmentCount.class, spec);
        query.chart(employees, DepartmentCount.class, spec);

        assertEquals(1L, PojoLens.getStatsPlanCacheMisses());
        assertEquals(1L, PojoLens.getStatsPlanCacheHits());
    }

    private static void runStatsPlanQuery(List<Employee> employees) {
        PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addCount("total")
                .initFilter()
                .filter(DepartmentCount.class);
    }

    private static void resetSqlLikePolicyDefaults() {
        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        PojoLens.setSqlLikeCacheMaxEntries(256);
        PojoLens.setSqlLikeCacheMaxWeight(0L);
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();
    }

    private static void resetStatsPlanPolicyDefaults() {
        PojoLens.setStatsPlanCacheEnabled(true);
        PojoLens.setStatsPlanCacheStatsEnabled(true);
        PojoLens.setStatsPlanCacheMaxEntries(512);
        PojoLens.setStatsPlanCacheMaxWeight(0L);
        PojoLens.setStatsPlanCacheExpireAfterWriteMillis(0L);
        PojoLens.clearStatsPlanCache();
        PojoLens.resetStatsPlanCacheStats();
    }

    public static class DepartmentCount {
        public String department;
        public long total;

        public DepartmentCount() {
        }
    }

    public static class DepartmentCountAlias {
        public String dept;
        public long total;

        public DepartmentCountAlias() {
        }
    }
}

