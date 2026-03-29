package laughing.man.commits;

import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCountAlias;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CachePolicyConfigTest {

    private PojoLensRuntime runtime;

    @BeforeEach
    public void setUp() {
        runtime = new PojoLensRuntime();
        resetSqlLikePolicyDefaults();
        resetStatsPlanPolicyDefaults();
    }

    @Test
    public void sqlLikeCacheShouldExpireEntriesWhenConfigured() throws Exception {
        runtime.sqlLikeCache().setExpireAfterWriteMillis(5L);
        runtime.sqlLikeCache().setMaxEntries(64);
        runtime.sqlLikeCache().clear();
        runtime.sqlLikeCache().resetStats();

        SqlLikeQuery first = runtime.parse("where stringField = 'policy'");
        Thread.sleep(30L);
        SqlLikeQuery second = runtime.parse("where stringField = 'policy'");

        assertNotSame(first, second);
        assertTrue(runtime.sqlLikeCache().getMisses() >= 2L);
        assertTrue(runtime.sqlLikeCache().getEvictions() >= 1L);
    }

    @Test
    public void sqlLikeCacheStatsToggleShouldSuppressCounters() {
        runtime.sqlLikeCache().setStatsEnabled(false);
        runtime.sqlLikeCache().clear();
        runtime.sqlLikeCache().resetStats();

        runtime.parse("where stringField = 'a'");
        runtime.parse("where stringField = 'a'");

        assertEquals(0L, runtime.sqlLikeCache().getHits());
        assertEquals(0L, runtime.sqlLikeCache().getMisses());
        assertEquals(0L, runtime.sqlLikeCache().getEvictions());
    }

    @Test
    public void statsPlanCacheShouldExpireEntriesWhenConfigured() throws Exception {
        runtime.statsPlanCache().setExpireAfterWriteMillis(5L);
        runtime.statsPlanCache().setMaxEntries(64);
        runtime.statsPlanCache().clear();
        runtime.statsPlanCache().resetStats();

        runStatsPlanQuery(sampleEmployees());
        Thread.sleep(30L);
        runStatsPlanQuery(sampleEmployees());

        assertTrue(runtime.statsPlanCache().misses() >= 2L);
        assertTrue(runtime.statsPlanCache().evictions() >= 1L);
    }

    @Test
    public void statsPlanCacheStatsToggleShouldSuppressCounters() {
        List<Employee> employees = sampleEmployees();
        runtime.statsPlanCache().setStatsEnabled(false);
        runtime.statsPlanCache().clear();
        runtime.statsPlanCache().resetStats();

        runStatsPlanQuery(employees);
        runStatsPlanQuery(employees);

        assertEquals(0L, runtime.statsPlanCache().hits());
        assertEquals(0L, runtime.statsPlanCache().misses());
        assertEquals(0L, runtime.statsPlanCache().evictions());
    }

    @Test
    public void sqlLikeAliasedStatsQueryShouldReuseStatsPlanCache() {
        List<Employee> employees = sampleEmployees();
        SqlLikeQuery query = runtime.parse("select department as dept, count(*) as total group by department");

        query.filter(employees, DepartmentCountAlias.class);
        query.filter(employees, DepartmentCountAlias.class);

        assertEquals(1L, runtime.statsPlanCache().misses());
        assertEquals(1L, runtime.statsPlanCache().hits());
    }

    @Test
    public void sqlLikeStatsChartShouldReuseStatsPlanCache() {
        List<Employee> employees = sampleEmployees();
        SqlLikeQuery query = runtime.parse("select department, count(*) as total group by department");
        ChartSpec spec = ChartSpec.of(ChartType.BAR, "department", "total");

        query.chart(employees, DepartmentCount.class, spec);
        query.chart(employees, DepartmentCount.class, spec);

        assertEquals(1L, runtime.statsPlanCache().misses());
        assertEquals(1L, runtime.statsPlanCache().hits());
    }

    @Test
    public void sqlLikeAliasedStatsChartShouldReuseStatsPlanCache() {
        List<Employee> employees = sampleEmployees();
        SqlLikeQuery query = runtime.parse("select department as dept, count(*) as total group by department");
        ChartSpec spec = ChartSpec.of(ChartType.BAR, "dept", "total");

        query.chart(employees, DepartmentCountAlias.class, spec);
        query.chart(employees, DepartmentCountAlias.class, spec);

        assertEquals(1L, runtime.statsPlanCache().misses());
        assertEquals(1L, runtime.statsPlanCache().hits());
    }

    private void runStatsPlanQuery(List<Employee> employees) {
        runtime.newQueryBuilder(employees)
                .addGroup("department")
                .addCount("total")
                .initFilter()
                .filter(DepartmentCount.class);
    }

    private void resetSqlLikePolicyDefaults() {
        runtime.sqlLikeCache().setEnabled(true);
        runtime.sqlLikeCache().setStatsEnabled(true);
        runtime.sqlLikeCache().setMaxEntries(256);
        runtime.sqlLikeCache().setMaxWeight(0L);
        runtime.sqlLikeCache().setExpireAfterWriteMillis(0L);
        runtime.sqlLikeCache().clear();
        runtime.sqlLikeCache().resetStats();
    }

    private void resetStatsPlanPolicyDefaults() {
        runtime.statsPlanCache().setEnabled(true);
        runtime.statsPlanCache().setStatsEnabled(true);
        runtime.statsPlanCache().setMaxEntries(512);
        runtime.statsPlanCache().setMaxWeight(0L);
        runtime.statsPlanCache().setExpireAfterWriteMillis(0L);
        runtime.statsPlanCache().clear();
        runtime.statsPlanCache().resetStats();
    }
}


