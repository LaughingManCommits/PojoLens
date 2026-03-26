package laughing.man.commits.testing;

import laughing.man.commits.PojoLensCore;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.SqlLikeQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.Objects;
import java.util.function.Function;

/**
 * Test utility for asserting parity between fluent and SQL-like query results.
 */
public final class FluentSqlLikeParity {

    private FluentSqlLikeParity() {
    }

    public static <T> void assertOrderedEquals(List<? extends T> fluentRows,
                                               List<? extends T> sqlLikeRows) {
        assertOrderedEquals(fluentRows, sqlLikeRows, Function.identity());
    }

    public static <T> void assertOrderedEquals(List<? extends T> fluentRows,
                                               List<? extends T> sqlLikeRows,
                                               Function<? super T, ?> normalizer) {
        List<Object> left = normalize(fluentRows, normalizer);
        List<Object> right = normalize(sqlLikeRows, normalizer);
        if (!left.equals(right)) {
            throw new AssertionError("Fluent/SQL-like ordered parity mismatch. Fluent="
                    + left
                    + ", SQL-like="
                    + right);
        }
    }

    public static <T> void assertUnorderedEquals(List<? extends T> fluentRows,
                                                 List<? extends T> sqlLikeRows) {
        assertUnorderedEquals(fluentRows, sqlLikeRows, Function.identity());
    }

    public static <T> void assertUnorderedEquals(List<? extends T> fluentRows,
                                                 List<? extends T> sqlLikeRows,
                                                 Function<? super T, ?> normalizer) {
        Map<Object, Integer> left = counts(normalize(fluentRows, normalizer));
        Map<Object, Integer> right = counts(normalize(sqlLikeRows, normalizer));
        if (!left.equals(right)) {
            throw new AssertionError("Fluent/SQL-like unordered parity mismatch. Fluent="
                    + left
                    + ", SQL-like="
                    + right);
        }
    }

    public static <T> void assertOrderedEquals(QuerySnapshotFixture snapshot,
                                               Class<T> projectionClass,
                                               BiConsumer<? super QueryBuilder, ? super QuerySnapshotFixture> fluentConfigurer,
                                               SqlLikeQuery sqlLikeQuery,
                                               Function<? super T, ?> normalizer) {
        assertOrderedEquals(
                executeFluent(snapshot, projectionClass, fluentConfigurer, sqlLikeQuery.sort()),
                sqlLikeQuery.filter(snapshot.bundle(), projectionClass),
                normalizer
        );
    }

    public static <T> void assertUnorderedEquals(QuerySnapshotFixture snapshot,
                                                 Class<T> projectionClass,
                                                 BiConsumer<? super QueryBuilder, ? super QuerySnapshotFixture> fluentConfigurer,
                                                 SqlLikeQuery sqlLikeQuery,
                                                 Function<? super T, ?> normalizer) {
        assertUnorderedEquals(
                executeFluent(snapshot, projectionClass, fluentConfigurer, sqlLikeQuery.sort()),
                sqlLikeQuery.filter(snapshot.bundle(), projectionClass),
                normalizer
        );
    }

    public static <T> void assertOrderedEquals(QuerySnapshotFixture snapshot,
                                               Class<T> projectionClass,
                                               java.util.function.Consumer<? super QueryBuilder> fluentConfigurer,
                                               SqlLikeQuery sqlLikeQuery,
                                               Function<? super T, ?> normalizer) {
        Objects.requireNonNull(fluentConfigurer, "fluentConfigurer must not be null");
        assertOrderedEquals(snapshot, projectionClass, (builder, ignored) -> fluentConfigurer.accept(builder), sqlLikeQuery, normalizer);
    }

    public static <T> void assertUnorderedEquals(QuerySnapshotFixture snapshot,
                                                 Class<T> projectionClass,
                                                 java.util.function.Consumer<? super QueryBuilder> fluentConfigurer,
                                                 SqlLikeQuery sqlLikeQuery,
                                                 Function<? super T, ?> normalizer) {
        Objects.requireNonNull(fluentConfigurer, "fluentConfigurer must not be null");
        assertUnorderedEquals(snapshot, projectionClass, (builder, ignored) -> fluentConfigurer.accept(builder), sqlLikeQuery, normalizer);
    }

    private static <T> List<Object> normalize(List<? extends T> rows,
                                              Function<? super T, ?> normalizer) {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(normalizer, "normalizer must not be null");
        List<Object> normalized = new ArrayList<>(rows.size());
        for (T row : rows) {
            normalized.add(normalizer.apply(row));
        }
        return normalized;
    }

    private static Map<Object, Integer> counts(List<Object> normalizedRows) {
        LinkedHashMap<Object, Integer> counts = new LinkedHashMap<>();
        for (Object row : normalizedRows) {
            counts.merge(row, 1, Integer::sum);
        }
        return counts;
    }

    private static <T> List<T> executeFluent(QuerySnapshotFixture snapshot,
                                             Class<T> projectionClass,
                                             BiConsumer<? super QueryBuilder, ? super QuerySnapshotFixture> fluentConfigurer,
                                             Sort sort) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        Objects.requireNonNull(fluentConfigurer, "fluentConfigurer must not be null");
        QueryBuilder builder = PojoLensCore.newQueryBuilder(snapshot.primaryRows());
        fluentConfigurer.accept(builder, snapshot);
        if (sort == null) {
            return builder.initFilter().filter(projectionClass);
        }
        return builder.initFilter().filter(sort, projectionClass);
    }
}

