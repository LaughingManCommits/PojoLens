package laughing.man.commits.report;

import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.util.StringUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned saved-report contract covering query text, parameters, schema,
 * and chart configuration.
 *
 * <p>A {@code SavedReport} carries only plain, serialization-friendly data —
 * no lambdas or live executors. It can be stored, reviewed with
 * {@link #planPreview()} or {@link #diagnostics()}, and replayed via
 * {@link #toDefinition(Class)} without re-parsing the original query text.
 *
 * <p>Format version {@value #FORMAT_VERSION} identifies the contract revision.
 * Deserializing code should validate this field for forward-compatibility.
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * SavedReport report = SavedReport
 *     .sqlLike("rpt-active-by-dept", "Active employees by department",
 *              "where active = :active group by department order by department asc")
 *     .withDefaultParam("active", true)
 *     .withChartSpec(ChartSpec.of(ChartType.BAR, "department", "total"));
 *
 * // review before running
 * SqlLikePlanPreview preview = report.planPreview();
 *
 * // replay against live data
 * ReportDefinition<DeptRow> def = report.toDefinition(DeptRow.class);
 * List<DeptRow> rows = def.rows(employeeSnapshot);
 * }</pre>
 */
public final class SavedReport {

    public static final String FORMAT_VERSION = "1";

    private final String id;
    private final String name;
    private final SavedReportKind kind;
    private final String queryText;
    private final String source;
    private final Map<String, Object> defaultParams;
    private final ChartSpec chartSpec;
    private final TabularSchema schema;

    private SavedReport(String id,
                        String name,
                        SavedReportKind kind,
                        String queryText,
                        String source,
                        Map<String, Object> defaultParams,
                        ChartSpec chartSpec,
                        TabularSchema schema) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.queryText = requireText(queryText, "queryText");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.defaultParams = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(defaultParams, "defaultParams must not be null")));
        this.chartSpec = chartSpec;
        this.schema = schema;
    }

    /**
     * Creates a saved report backed by an SQL-like query.
     * The query is parsed immediately to derive the source name and validate syntax.
     */
    public static SavedReport sqlLike(String id, String name, String queryText) {
        requireText(id, "id");
        requireText(name, "name");
        requireText(queryText, "queryText");
        String source = SqlLikeQuery.of(queryText).planPreview().source();
        return new SavedReport(id, name, SavedReportKind.SQL_LIKE, queryText, source,
                Collections.emptyMap(), null, null);
    }

    /**
     * Creates a saved report backed by a natural-language query.
     * The query is parsed immediately to derive the source name and validate syntax.
     */
    public static SavedReport natural(String id, String name, String queryText) {
        requireText(id, "id");
        requireText(name, "name");
        requireText(queryText, "queryText");
        String source = NaturalQuery.of(queryText).source();
        return new SavedReport(id, name, SavedReportKind.NATURAL, queryText, source,
                Collections.emptyMap(), null, null);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Contract format version; always {@value #FORMAT_VERSION} for this release. */
    public String version() {
        return FORMAT_VERSION;
    }

    public SavedReportKind kind() {
        return kind;
    }

    public String queryText() {
        return queryText;
    }

    /** Primary data-source name derived from the query at creation time. */
    public String source() {
        return source;
    }

    /** Default parameter values applied during replay when the caller supplies none. */
    public Map<String, Object> defaultParams() {
        return defaultParams;
    }

    /** Optional chart configuration; {@code null} if not set. */
    public ChartSpec chartSpec() {
        return chartSpec;
    }

    /** Optional explicit output schema; {@code null} if not set. */
    public TabularSchema schema() {
        return schema;
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    public SavedReport withDefaultParam(String paramName, Object value) {
        requireText(paramName, "paramName");
        var merged = new LinkedHashMap<>(defaultParams);
        merged.put(paramName, value);
        return new SavedReport(id, name, kind, queryText, source, merged, chartSpec, schema);
    }

    public SavedReport withDefaultParams(Map<String, Object> params) {
        Objects.requireNonNull(params, "params must not be null");
        var merged = new LinkedHashMap<>(defaultParams);
        merged.putAll(params);
        return new SavedReport(id, name, kind, queryText, source, merged, chartSpec, schema);
    }

    public SavedReport withChartSpec(ChartSpec spec) {
        Objects.requireNonNull(spec, "chartSpec must not be null");
        return new SavedReport(id, name, kind, queryText, source, defaultParams, spec, schema);
    }

    public SavedReport withSchema(TabularSchema value) {
        Objects.requireNonNull(value, "schema must not be null");
        return new SavedReport(id, name, kind, queryText, source, defaultParams, chartSpec, value);
    }

    // -------------------------------------------------------------------------
    // Review — safe, no data required
    // -------------------------------------------------------------------------

    /**
     * Returns a structural plan preview of the query without executing it.
     * Safe for logging, admin review, and saved-report validation.
     */
    public SqlLikePlanPreview planPreview() {
        return switch (kind) {
            case SQL_LIKE -> SqlLikeQuery.of(queryText).planPreview();
            case NATURAL -> SqlLikeQuery.of(NaturalQuery.of(queryText).equivalentSqlLike()).planPreview();
        };
    }

    /**
     * Returns diagnostics (field references, required params, lint warnings)
     * without executing the query.
     */
    public QueryDiagnostics diagnostics() {
        return switch (kind) {
            case SQL_LIKE -> SqlLikeQuery.of(queryText).diagnostics();
            case NATURAL -> NaturalQuery.of(queryText).diagnostics();
        };
    }

    // -------------------------------------------------------------------------
    // Replay
    // -------------------------------------------------------------------------

    /**
     * Reconstructs a live {@link SqlLikeQuery} with default parameters applied.
     *
     * @throws IllegalStateException if this report's kind is not {@link SavedReportKind#SQL_LIKE}
     */
    public SqlLikeQuery toQuery() {
        if (kind != SavedReportKind.SQL_LIKE) {
            throw new IllegalStateException(
                    "SavedReport kind is " + kind + "; use toNaturalQuery() for NATURAL reports");
        }
        SqlLikeQuery q = SqlLikeQuery.of(queryText);
        return defaultParams.isEmpty() ? q : q.params(defaultParams);
    }

    /**
     * Reconstructs a live {@link NaturalQuery} with default parameters applied.
     *
     * @throws IllegalStateException if this report's kind is not {@link SavedReportKind#NATURAL}
     */
    public NaturalQuery toNaturalQuery() {
        if (kind != SavedReportKind.NATURAL) {
            throw new IllegalStateException(
                    "SavedReport kind is " + kind + "; use toQuery() for SQL_LIKE reports");
        }
        NaturalQuery q = NaturalQuery.of(queryText);
        return defaultParams.isEmpty() ? q : q.params(defaultParams);
    }

    /**
     * Replays the saved report as a live {@link ReportDefinition} for the given
     * row projection type. Default parameters, chart spec, and schema are applied
     * when present.
     */
    public <T> ReportDefinition<T> toDefinition(Class<T> rowType) {
        Objects.requireNonNull(rowType, "rowType must not be null");
        ReportDefinition<T> def = switch (kind) {
            case SQL_LIKE -> ReportDefinition.sql(toQuery(), rowType);
            case NATURAL -> ReportDefinition.natural(toNaturalQuery(), rowType);
        };
        if (chartSpec != null) {
            def = def.withChartSpec(chartSpec);
        }
        if (schema != null) {
            def = def.withSchema(schema);
        }
        return def;
    }

    private static String requireText(String value, String field) {
        if (StringUtil.isNullOrBlank(value)) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
        return value;
    }
}
