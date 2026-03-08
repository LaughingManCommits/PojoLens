package laughing.man.commits.testing;

import laughing.man.commits.PojoLens;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.sqllike.SqlLikeQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Code-first regression fixture for asserting query/report behavior over a
 * named immutable dataset snapshot.
 *
 * @param <T> projection row type
 */
public final class QueryRegressionFixture<T> {

    private final QuerySnapshotFixture snapshot;
    private final String source;
    private final FixtureExecutor<T> executor;
    private final ExplainSupplier explainSupplier;
    private final LintSupplier lintSupplier;

    private List<T> cachedRows;
    private Map<String, Object> cachedExplain;
    private List<String> cachedLintCodes;

    private QueryRegressionFixture(QuerySnapshotFixture snapshot,
                                   String source,
                                   FixtureExecutor<T> executor,
                                   ExplainSupplier explainSupplier,
                                   LintSupplier lintSupplier) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.explainSupplier = explainSupplier;
        this.lintSupplier = lintSupplier;
    }

    public static <T> QueryRegressionFixture<T> sql(QuerySnapshotFixture snapshot,
                                                    SqlLikeQuery query,
                                                    Class<T> projectionClass) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        return new QueryRegressionFixture<>(
                snapshot,
                query.source(),
                current -> query.filter(current.bundle(), projectionClass),
                current -> query.explain(current.bundle(), projectionClass),
                () -> lintCodes(query.lintWarnings())
        );
    }

    public static <T> QueryRegressionFixture<T> report(QuerySnapshotFixture snapshot,
                                                       ReportDefinition<T> report) {
        Objects.requireNonNull(report, "report must not be null");
        return new QueryRegressionFixture<>(
                snapshot,
                report.source(),
                current -> report.rows(current.bundle()),
                null,
                null
        );
    }

    public static <T> QueryRegressionFixture<T> fluent(QuerySnapshotFixture snapshot,
                                                       Class<T> projectionClass,
                                                       Consumer<QueryBuilder> configurer) {
        Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        Objects.requireNonNull(configurer, "configurer must not be null");
        return new QueryRegressionFixture<>(
                snapshot,
                "fluent",
                current -> {
                    QueryBuilder builder = PojoLens.newQueryBuilder(current.primaryRows());
                    configurer.accept(builder);
                    return builder.initFilter().filter(projectionClass);
                },
                null,
                null
        );
    }

    public QuerySnapshotFixture snapshot() {
        return snapshot;
    }

    public String source() {
        return source;
    }

    public List<T> rows() {
        if (cachedRows == null) {
            cachedRows = Collections.unmodifiableList(new ArrayList<>(executor.execute(snapshot)));
        }
        return cachedRows;
    }

    public Map<String, Object> explain() {
        if (explainSupplier == null) {
            throw new IllegalStateException("Fixture '" + snapshot.name() + "' does not support explain()");
        }
        if (cachedExplain == null) {
            cachedExplain = Collections.unmodifiableMap(new LinkedHashMap<>(explainSupplier.explain(snapshot)));
        }
        return cachedExplain;
    }

    public List<String> lintCodes() {
        if (lintSupplier == null) {
            throw new IllegalStateException("Fixture '" + snapshot.name() + "' does not support lintCodes()");
        }
        if (cachedLintCodes == null) {
            cachedLintCodes = Collections.unmodifiableList(new ArrayList<>(lintSupplier.codes()));
        }
        return cachedLintCodes;
    }

    public QueryRegressionFixture<T> assertRowCount(int expectedCount) {
        int actual = rows().size();
        if (actual != expectedCount) {
            throw new AssertionError(prefix() + " expected rowCount=" + expectedCount + " but was " + actual);
        }
        return this;
    }

    public QueryRegressionFixture<T> assertMetric(String label,
                                                  Function<? super List<T>, ?> extractor,
                                                  Object expected) {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(extractor, "extractor must not be null");
        Object actual = extractor.apply(rows());
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError(prefix() + " metric '" + label + "' mismatch. expected="
                    + expected
                    + ", actual="
                    + actual);
        }
        return this;
    }

    public QueryRegressionFixture<T> assertOrderedRows(Function<? super T, ?> normalizer, Object... expected) {
        return assertOrderedRows(normalizer, List.of(expected));
    }

    public QueryRegressionFixture<T> assertOrderedRows(Function<? super T, ?> normalizer, List<?> expected) {
        List<Object> actual = normalize(rows(), normalizer);
        if (!actual.equals(expected)) {
            throw new AssertionError(prefix() + " ordered rows mismatch. expected="
                    + expected
                    + ", actual="
                    + actual);
        }
        return this;
    }

    public QueryRegressionFixture<T> assertUnorderedRows(Function<? super T, ?> normalizer, Object... expected) {
        return assertUnorderedRows(normalizer, List.of(expected));
    }

    public QueryRegressionFixture<T> assertUnorderedRows(Function<? super T, ?> normalizer, List<?> expected) {
        Map<Object, Integer> actualCounts = counts(normalize(rows(), normalizer));
        Map<Object, Integer> expectedCounts = counts(new ArrayList<>(expected));
        if (!actualCounts.equals(expectedCounts)) {
            throw new AssertionError(prefix() + " unordered rows mismatch. expected="
                    + expectedCounts
                    + ", actual="
                    + actualCounts);
        }
        return this;
    }

    public QueryRegressionFixture<T> assertExplainContains(Map<String, ?> expectedSubset) {
        Objects.requireNonNull(expectedSubset, "expectedSubset must not be null");
        Map<String, Object> actual = explain();
        if (!containsSubset(actual, expectedSubset)) {
            throw new AssertionError(prefix() + " explain payload mismatch. expected subset="
                    + expectedSubset
                    + ", actual="
                    + actual);
        }
        return this;
    }

    public QueryRegressionFixture<T> assertLintCodes(String... expectedCodes) {
        List<String> actual = lintCodes();
        List<String> expected = List.of(expectedCodes);
        if (!actual.equals(expected)) {
            throw new AssertionError(prefix() + " lint codes mismatch. expected="
                    + expected
                    + ", actual="
                    + actual);
        }
        return this;
    }

    private String prefix() {
        return "Regression fixture[snapshot=" + snapshot.name() + ", source=" + source + "]";
    }

    private static List<String> lintCodes(List<SqlLikeLintWarning> warnings) {
        ArrayList<String> codes = new ArrayList<>(warnings.size());
        for (SqlLikeLintWarning warning : warnings) {
            codes.add(warning.code());
        }
        return codes;
    }

    private static <T> List<Object> normalize(List<? extends T> rows, Function<? super T, ?> normalizer) {
        Objects.requireNonNull(normalizer, "normalizer must not be null");
        ArrayList<Object> normalized = new ArrayList<>(rows.size());
        for (T row : rows) {
            normalized.add(normalizer.apply(row));
        }
        return normalized;
    }

    private static Map<Object, Integer> counts(List<?> values) {
        LinkedHashMap<Object, Integer> counts = new LinkedHashMap<>();
        for (Object value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }

    private static boolean containsSubset(Object actual, Object expected) {
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)) {
                return false;
            }
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                if (!actualMap.containsKey(entry.getKey())) {
                    return false;
                }
                if (!containsSubset(actualMap.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof List<?> expectedList) {
            return Objects.equals(actual, expectedList);
        }
        return Objects.equals(actual, expected);
    }

    @FunctionalInterface
    private interface FixtureExecutor<T> {
        List<T> execute(QuerySnapshotFixture snapshot);
    }

    @FunctionalInterface
    private interface ExplainSupplier {
        Map<String, Object> explain(QuerySnapshotFixture snapshot);
    }

    @FunctionalInterface
    private interface LintSupplier {
        List<String> codes();
    }
}

