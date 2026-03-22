package laughing.man.commits;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CacheConcurrencyTest {

    @BeforeEach
    public void setUp() {
        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        PojoLens.setSqlLikeCacheMaxEntries(64);
        PojoLens.setSqlLikeCacheMaxWeight(0L);
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();

        PojoLens.setStatsPlanCacheEnabled(true);
        PojoLens.setStatsPlanCacheStatsEnabled(true);
        PojoLens.setStatsPlanCacheMaxEntries(16);
        PojoLens.setStatsPlanCacheMaxWeight(0L);
        PojoLens.setStatsPlanCacheExpireAfterWriteMillis(0L);
        PojoLens.clearStatsPlanCache();
        PojoLens.resetStatsPlanCacheStats();
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

        PojoLens.setStatsPlanCacheEnabled(true);
        PojoLens.setStatsPlanCacheStatsEnabled(true);
        PojoLens.setStatsPlanCacheMaxEntries(512);
        PojoLens.setStatsPlanCacheMaxWeight(0L);
        PojoLens.setStatsPlanCacheExpireAfterWriteMillis(0L);
        PojoLens.clearStatsPlanCache();
        PojoLens.resetStatsPlanCacheStats();
    }

    @Test
    public void sqlLikeCacheShouldRemainCoherentUnderConcurrentParses() throws Exception {
        String[] queries = new String[] {
                "where department = 'Engineering'",
                "where department = 'Finance'",
                "where salary >= 90000",
                "where active = true",
                "where salary >= 100000 and active = true"
        };
        int threads = 8;
        int perThreadOps = 200;
        int totalOps = threads * perThreadOps;

        runConcurrently(threads, 20, threadIndex -> {
            for (int i = 0; i < perThreadOps; i++) {
                PojoLens.parse(queries[(i + threadIndex) % queries.length]);
            }
        });

        long hits = PojoLens.getSqlLikeCacheHits();
        long misses = PojoLens.getSqlLikeCacheMisses();
        assertEquals(totalOps, hits + misses);
        assertTrue(PojoLens.getSqlLikeCacheSize() <= PojoLens.getSqlLikeCacheMaxEntries());

        Map<String, Object> snapshot = PojoLens.getSqlLikeCacheSnapshot();
        assertEquals(hits, ((Number) snapshot.get("hits")).longValue());
        assertEquals(misses, ((Number) snapshot.get("misses")).longValue());
        assertEquals(PojoLens.getSqlLikeCacheEvictions(), ((Number) snapshot.get("evictions")).longValue());
    }

    @Test
    public void statsPlanCacheShouldRemainCoherentUnderConcurrentPlans() throws Exception {
        List<Employee> employees = sampleEmployees();
        PojoLens.setStatsPlanCacheMaxEntries(1);

        int threads = 8;
        int perThreadOps = 120;
        runConcurrently(threads, 20, threadIndex -> {
            int mode = threadIndex % 3;
            for (int i = 0; i < perThreadOps; i++) {
                if (mode == 0) {
                    PojoLens.newQueryBuilder(employees)
                            .addGroup("department")
                            .addCount("total")
                            .initFilter()
                            .filter(DepartmentCount.class);
                } else if (mode == 1) {
                    PojoLens.newQueryBuilder(employees)
                            .addGroup("active")
                            .addCount("total")
                            .initFilter()
                            .filter(ActiveCount.class);
                } else {
                    PojoLens.newQueryBuilder(employees)
                            .addGroup("department")
                            .addMetric("salary", Metric.SUM, "totalSalary")
                            .initFilter()
                            .filter(DepartmentSalary.class);
                }
            }
        });

        assertTrue(PojoLens.getStatsPlanCacheMisses() > 0L);
        assertTrue(PojoLens.getStatsPlanCacheHits() > 0L);
        assertTrue(PojoLens.getStatsPlanCacheEvictions() > 0L);
        assertTrue(PojoLens.getStatsPlanCacheSize() <= PojoLens.getStatsPlanCacheMaxEntries());

        Map<String, Object> snapshot = PojoLens.getStatsPlanCacheSnapshot();
        assertEquals(PojoLens.getStatsPlanCacheHits(), ((Number) snapshot.get("hits")).longValue());
        assertEquals(PojoLens.getStatsPlanCacheMisses(), ((Number) snapshot.get("misses")).longValue());
        assertEquals(PojoLens.getStatsPlanCacheEvictions(), ((Number) snapshot.get("evictions")).longValue());
    }

    @Test
    public void statsPlanCacheShouldHitForEquivalentRuleShapesAcrossBuilders() {
        List<Employee> employees = sampleEmployees();

        PojoLens.newQueryBuilder(employees)
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total")
                .initFilter()
                .filter(DepartmentCount.class);

        PojoLens.newQueryBuilder(employees)
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total")
                .initFilter()
                .filter(DepartmentCount.class);

        assertEquals(1L, PojoLens.getStatsPlanCacheMisses());
        assertEquals(1L, PojoLens.getStatsPlanCacheHits());
    }

    private static void runConcurrently(int threads,
                                        int timeoutSeconds,
                                        Worker worker) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        try {
            for (int threadIndex = 0; threadIndex < threads; threadIndex++) {
                final int currentThreadIndex = threadIndex;
                pool.submit(() -> {
                    try {
                        start.await();
                        worker.run(currentThreadIndex);
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(timeoutSeconds, TimeUnit.SECONDS), "Concurrent task timeout");
            assertTrue(errors.isEmpty(), "Concurrent worker failed: " + errors.peek());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "Executor termination timeout");
        }
    }

    @FunctionalInterface
    private interface Worker {
        void run(int threadIndex) throws Exception;
    }

    public static class DepartmentCount {
        public String department;
        public long total;

        public DepartmentCount() {
        }
    }

    public static class ActiveCount {
        public boolean active;
        public long total;

        public ActiveCount() {
        }
    }

    public static class DepartmentSalary {
        public String department;
        public long totalSalary;

        public DepartmentSalary() {
        }
    }
}

