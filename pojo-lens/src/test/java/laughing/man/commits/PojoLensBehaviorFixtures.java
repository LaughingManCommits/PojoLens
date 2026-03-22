package laughing.man.commits;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import org.junit.jupiter.params.provider.Arguments;
import laughing.man.commits.enums.Sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PojoLensBehaviorFixtures {

    private PojoLensBehaviorFixtures() {
    }

    public static class BoolBean {
        String name;
        boolean active;

        BoolBean() {
        }

        BoolBean(String name, boolean active) {
            this.name = name;
            this.active = active;
        }
    }

    public static class ParentBean {
        int id;
        String name;

        ParentBean() {
        }

        ParentBean(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class ChildBean {
        int parentId;
        String tag;

        ChildBean() {
        }

        ChildBean(int parentId, String tag) {
            this.parentId = parentId;
            this.tag = tag;
        }
    }

    public static class ChildCollisionBean {
        int parentId;
        String name;

        ChildCollisionBean() {
        }

        ChildCollisionBean(int parentId, String name) {
            this.parentId = parentId;
            this.name = name;
        }
    }

    public static class JoinedCollisionProjection {
        int id;
        String name;
        int parentId;
        String child_name;

        JoinedCollisionProjection() {
        }
    }

    public static class ParentCollisionBean {
        int id;
        String name;
        String child_name;

        ParentCollisionBean() {
        }

        ParentCollisionBean(int id, String name, String child_name) {
            this.id = id;
            this.name = name;
            this.child_name = child_name;
        }
    }

    public static class ParentMultiCollisionBean {
        int id;
        String name;
        String child_name;
        String child_name_1;

        ParentMultiCollisionBean() {
        }

        ParentMultiCollisionBean(int id, String name, String child_name, String child_name_1) {
            this.id = id;
            this.name = name;
            this.child_name = child_name;
            this.child_name_1 = child_name_1;
        }
    }

    public static class JoinedCollisionProjectionWithSuffix {
        int id;
        String name;
        int parentId;
        String child_name;
        String child_name_1;

        JoinedCollisionProjectionWithSuffix() {
        }
    }

    public static class JoinedCollisionProjectionWithDeepSuffix {
        int id;
        String name;
        int parentId;
        String child_name;
        String child_name_1;
        String child_name_2;

        JoinedCollisionProjectionWithDeepSuffix() {
        }
    }

    public static class RightJoinCollisionProjection {
        int parentId;
        String name;
        int id;
        String child_name;
        String child_child_name;

        RightJoinCollisionProjection() {
        }
    }

    static void assertSingleIntegerResult(List<Foo> source,
                                          Clauses clause,
                                          int compareValue,
                                          int expectedInteger) throws Exception {
        List<Foo> results = PojoLens.newQueryBuilder(source)
                .addRule("integerField", compareValue, clause, Separator.OR)
                .initFilter()
                .filter(Foo.class);
        assertEquals(1, results.size());
        assertEquals(expectedInteger, results.get(0).getIntegerField());
    }

    static List<Foo> numericClauseSource() {
        return Arrays.asList(
                new Foo("abc", new Date(), 10),
                new Foo("bcd", new Date(), 20),
                new Foo("123", new Date(), 30)
        );
    }

    static Stream<Arguments> numericClauseCases() {
        return Stream.of(
                Arguments.of("equal", Clauses.EQUAL, 20, 20),
                Arguments.of("bigger", Clauses.BIGGER, 20, 30),
                Arguments.of("bigger-equal", Clauses.BIGGER_EQUAL, 30, 30),
                Arguments.of("smaller", Clauses.SMALLER, 20, 10),
                Arguments.of("smaller-equal", Clauses.SMALLER_EQUAL, 10, 10),
                Arguments.of("not-bigger", Clauses.NOT_BIGGER, 10, 10),
                Arguments.of("not-smaller", Clauses.NOT_SMALLER, 30, 30)
        );
    }

    static Stream<Arguments> separatorCases() {
        return Stream.of(
                Arguments.of("and", Separator.AND, 1),
                Arguments.of("or", Separator.OR, 3)
        );
    }

    static Stream<Arguments> sortCases() {
        return Stream.of(
                Arguments.of("ascending", Sort.ASC, 1, 3),
                Arguments.of("descending", Sort.DESC, 3, 1)
        );
    }

    static <T> List<T> runConcurrent(int tasks,
                                     int threads,
                                     Callable<T> callable) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<T>> jobs = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                jobs.add(callable);
            }
            List<Future<T>> futures = executor.invokeAll(jobs);
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Concurrent task failed", e.getCause());
                }
            }
            return results;
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}

