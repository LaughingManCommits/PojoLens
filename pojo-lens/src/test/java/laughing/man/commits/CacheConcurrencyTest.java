package laughing.man.commits;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
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

    private PojoLensRuntime runtime;

    @BeforeEach
    public void setUp() {
        runtime = new PojoLensRuntime();

        runtime.sqlLikeCache().setEnabled(true);
        runtime.sqlLikeCache().setStatsEnabled(true);
        runtime.sqlLikeCache().setMaxEntries(64);
        runtime.sqlLikeCache().setMaxWeight(0L);
        runtime.sqlLikeCache().setExpireAfterWriteMillis(0L);
        runtime.sqlLikeCache().clear();
        runtime.sqlLikeCache().resetStats();

        runtime.statsPlanCache().setEnabled(true);
        runtime.statsPlanCache().setStatsEnabled(true);
        runtime.statsPlanCache().setMaxEntries(16);
        runtime.statsPlanCache().setMaxWeight(0L);
        runtime.statsPlanCache().setExpireAfterWriteMillis(0L);
        runtime.statsPlanCache().clear();
        runtime.statsPlanCache().resetStats();
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
                runtime.parse(queries[(i + threadIndex) % queries.length]);
            }
        });

        long hits = runtime.sqlLikeCache().getHits();
        long misses = runtime.sqlLikeCache().getMisses();
        assertEquals(totalOps, hits + misses);
        assertTrue(runtime.sqlLikeCache().getSize() <= runtime.sqlLikeCache().getMaxEntries());

        Map<String, Object> snapshot = runtime.sqlLikeCache().snapshot();
        assertEquals(hits, ((Number) snapshot.get("hits")).longValue());
        assertEquals(misses, ((Number) snapshot.get("misses")).longValue());
        assertEquals(runtime.sqlLikeCache().getEvictions(), ((Number) snapshot.get("evictions")).longValue());
    }

    @Test
    public void statsPlanCacheShouldRemainCoherentUnderConcurrentPlans() throws Exception {
        List<Employee> employees = sampleEmployees();
        runtime.statsPlanCache().setMaxEntries(1);

        int threads = 8;
        int perThreadOps = 120;
        runConcurrently(threads, 20, threadIndex -> {
            int mode = threadIndex % 3;
            for (int i = 0; i < perThreadOps; i++) {
                if (mode == 0) {
                    runtime.newQueryBuilder(employees)
                            .addGroup("department")
                            .addCount("total")
                            .initFilter()
                            .filter(DepartmentCount.class);
                } else if (mode == 1) {
                    runtime.newQueryBuilder(employees)
                            .addGroup("active")
                            .addCount("total")
                            .initFilter()
                            .filter(ActiveCount.class);
                } else {
                    runtime.newQueryBuilder(employees)
                            .addGroup("department")
                            .addMetric("salary", Metric.SUM, "totalSalary")
                            .initFilter()
                            .filter(DepartmentSalary.class);
                }
            }
        });

        assertTrue(runtime.statsPlanCache().misses() > 0L);
        assertTrue(runtime.statsPlanCache().hits() > 0L);
        assertTrue(runtime.statsPlanCache().evictions() > 0L);
        assertTrue(runtime.statsPlanCache().size() <= runtime.statsPlanCache().maxEntries());

        Map<String, Object> snapshot = runtime.statsPlanCache().snapshot();
        assertEquals(runtime.statsPlanCache().hits(), ((Number) snapshot.get("hits")).longValue());
        assertEquals(runtime.statsPlanCache().misses(), ((Number) snapshot.get("misses")).longValue());
        assertEquals(runtime.statsPlanCache().evictions(), ((Number) snapshot.get("evictions")).longValue());
    }

    @Test
    public void statsPlanCacheShouldHitForEquivalentRuleShapesAcrossBuilders() {
        List<Employee> employees = sampleEmployees();

        runtime.newQueryBuilder(employees)
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total")
                .initFilter()
                .filter(DepartmentCount.class);

        runtime.newQueryBuilder(employees)
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total")
                .initFilter()
                .filter(DepartmentCount.class);

        assertEquals(1L, runtime.statsPlanCache().misses());
        assertEquals(1L, runtime.statsPlanCache().hits());
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


