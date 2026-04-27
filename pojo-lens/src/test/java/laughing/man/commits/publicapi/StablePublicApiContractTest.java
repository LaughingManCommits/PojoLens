package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLensCsv;
import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensSql;
import laughing.man.commits.PojoLensChart;
import laughing.man.commits.PojoLensTree;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.PojoLensRuntimePreset;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.csv.CsvCoercionPolicy;
import laughing.man.commits.csv.CsvLoadException;
import laughing.man.commits.csv.CsvLoadReport;
import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.csv.CsvRuntime;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedPredicate;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.metamodel.FieldMetamodelGenerator;
import laughing.man.commits.natural.NaturalBoundQuery;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.natural.NaturalRuntime;
import laughing.man.commits.natural.NaturalTemplate;
import laughing.man.commits.natural.NaturalVocabulary;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.report.SavedReport;
import laughing.man.commits.table.TabularColumn;
import laughing.man.commits.sqllike.QueryComplexitySummary;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryDiagnosticsError;
import laughing.man.commits.sqllike.QueryCancellationToken;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExecutionGuardException;
import laughing.man.commits.sqllike.QueryExposurePolicy;
import laughing.man.commits.sqllike.QueryGuardOutcome;
import laughing.man.commits.sqllike.PlanPreviewField;
import laughing.man.commits.sqllike.PlanPreviewFilter;
import laughing.man.commits.sqllike.PlanPreviewJoin;
import laughing.man.commits.sqllike.PlanPreviewOrder;
import laughing.man.commits.sqllike.PlanPreviewPaging;
import laughing.man.commits.sqllike.PlanPreviewPredicate;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.sqllike.SqlLikeBoundQuery;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.sqllike.SqlLikePushdownAdapter;
import laughing.man.commits.sqllike.SqlLikePushdownException;
import laughing.man.commits.sqllike.SqlLikePushdownMode;
import laughing.man.commits.sqllike.SqlLikePushdownPreview;
import laughing.man.commits.sqllike.SqlLikePushdownRequest;
import laughing.man.commits.sqllike.SqlLikePushdownResult;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlLikeResultSetAdapter;
import laughing.man.commits.sqllike.SqlLikeTemplate;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.tree.TreeEntry;
import laughing.man.commits.tree.TreeTraversalBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StablePublicApiContractTest {

    @Test
    public void stableEntryPointFactoryMethodsShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(PojoLensNatural.class, "parse", String.class);
        requirePublicStaticMethod(PojoLensNatural.class, "template", String.class, String[].class);
        requirePublicStaticMethod(PojoLensSql.class, "parse", String.class);
        requirePublicStaticMethod(PojoLensSql.class, "template", String.class, String[].class);
        requirePublicStaticMethod(PojoLensCsv.class, "read", Path.class, Class.class);
        requirePublicStaticMethod(PojoLensCsv.class, "read", Path.class, Class.class, CsvOptions.class);
        requirePublicStaticMethod(PojoLensCsv.class, "readWithReport", Path.class, Class.class);
        requirePublicStaticMethod(PojoLensCsv.class, "readWithReport", Path.class, Class.class, CsvOptions.class);
        requirePublicStaticMethod(PojoLensChart.class, "toChartData", List.class, ChartSpec.class);
        requirePublicStaticMethod(PojoLensTree.class, "fromFlat", List.class, Function.class, Function.class);
        requirePublicStaticMethod(PojoLensTree.class, "subtreeOf", List.class, Function.class, Function.class, Object.class);
        requirePublicStaticMethod(PojoLensRuntime.class, "ofPreset", PojoLensRuntimePreset.class);
        requirePublicMethod(PojoLensRuntime.class, "natural");
        requirePublicMethod(PojoLensRuntime.class, "csv");
        requirePublicMethod(PojoLensRuntime.class, "sqlLikeCache");
        requirePublicMethod(PojoLensRuntime.class, "statsPlanCache");
        requirePublicMethod(PojoLensRuntime.class, "setNaturalVocabulary", NaturalVocabulary.class);
        requirePublicMethod(PojoLensRuntime.class, "getNaturalVocabulary");
        requirePublicMethod(PojoLensRuntime.class, "setQueryExposurePolicy", QueryExposurePolicy.class);
        requirePublicMethod(PojoLensRuntime.class, "getQueryExposurePolicy");
        requirePublicMethod(PojoLensRuntime.class, "setCsvDefaults", CsvOptions.class);
        requirePublicMethod(PojoLensRuntime.class, "getCsvDefaults");
        requirePublicStaticMethod(CsvCoercionPolicy.class, "builder");
        requirePublicStaticMethod(CsvCoercionPolicy.class, "defaults");
        requirePublicStaticMethod(CsvOptions.class, "builder");
        requirePublicStaticMethod(CsvOptions.class, "defaults");
        requirePublicMethod(CsvCoercionPolicy.class, "toBuilder");
        requirePublicMethod(CsvOptions.class, "coercionPolicy");
        requirePublicMethod(CsvOptions.class, "toBuilder");
        requirePublicMethod(CsvLoadResult.class, "rows");
        requirePublicMethod(CsvLoadResult.class, "report");
        requirePublicMethod(CsvLoadReport.class, "success");
        requirePublicMethod(CsvLoadReport.class, "resolvedSchema");
        requirePublicMethod(CsvLoadException.class, "report");
        requirePublicStaticMethod(DatasetBundle.class, "of", List.class);
        requirePublicStaticMethod(DatasetBundle.class, "of", List.class, JoinBindings.class);
    }

    @Test
    public void stableTreeContractsShouldRemainAvailable() throws Exception {
        requirePublicMethod(TreeTraversalBuilder.class, "subtree", Object.class);
        requirePublicMethod(TreeTraversalBuilder.class, "maxDepth", int.class);
        requirePublicMethod(TreeTraversalBuilder.class, "prune", Predicate.class);
        requirePublicMethod(TreeTraversalBuilder.class, "leavesOnly");
        requirePublicMethod(TreeTraversalBuilder.class, "toList");
        requirePublicMethod(TreeTraversalBuilder.class, "toEntries");
        requirePublicMethod(TreeEntry.class, "node");
        requirePublicMethod(TreeEntry.class, "depth");
        requirePublicMethod(TreeEntry.class, "parent");
    }

    @Test
    public void stableCsvRuntimeContractsShouldRemainAvailable() throws Exception {
        requirePublicMethod(CsvRuntime.class, "read", Path.class, Class.class);
        requirePublicMethod(CsvRuntime.class, "read", Path.class, Class.class, CsvOptions.class);
        requirePublicMethod(CsvRuntime.class, "readWithReport", Path.class, Class.class);
        requirePublicMethod(CsvRuntime.class, "readWithReport", Path.class, Class.class, CsvOptions.class);
    }

    @Test
    public void stableReportDefinitionContractsShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(ReportDefinition.class, "sql", SqlLikeQuery.class, Class.class);
        requirePublicStaticMethod(ReportDefinition.class, "sql", SqlLikeQuery.class, Class.class, ChartSpec.class);
        requirePublicStaticMethod(ReportDefinition.class, "natural", NaturalQuery.class, Class.class);
        requirePublicStaticMethod(ReportDefinition.class, "natural", NaturalQuery.class, Class.class, ChartSpec.class);
        requirePublicMethod(ReportDefinition.class, "source");
        requirePublicMethod(ReportDefinition.class, "projectionClass");
        requirePublicMethod(ReportDefinition.class, "chartSpec");
        requirePublicMethod(ReportDefinition.class, "schema");
        requirePublicMethod(ReportDefinition.class, "supportsJoinSources");
        requirePublicMethod(ReportDefinition.class, "withChartSpec", ChartSpec.class);
        requirePublicMethod(ReportDefinition.class, "rows", List.class);
        requirePublicMethod(ReportDefinition.class, "rows", List.class, JoinBindings.class);
        requirePublicMethod(ReportDefinition.class, "rows", DatasetBundle.class);
        requirePublicMethod(ReportDefinition.class, "chart", List.class);
        requirePublicMethod(ReportDefinition.class, "chart", List.class, JoinBindings.class);
        requirePublicMethod(ReportDefinition.class, "chart", DatasetBundle.class);
    }

    @Test
    public void stableSqlLikeContractsShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(SqlLikeQuery.class, "of", String.class);
        requirePublicMethod(SqlLikeQuery.class, "source");
        requirePublicMethod(SqlLikeQuery.class, "params", Map.class);
        requirePublicMethod(SqlLikeQuery.class, "params", SqlParams.class);
        requirePublicMethod(SqlLikeQuery.class, "keysetAfter", SqlLikeCursor.class);
        requirePublicMethod(SqlLikeQuery.class, "keysetBefore", SqlLikeCursor.class);
        requirePublicMethod(SqlLikeQuery.class, "bindTyped", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "bindTyped", List.class, Class.class, JoinBindings.class);
        requirePublicMethod(SqlLikeQuery.class, "filter", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "filter", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "filterPage", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "filterPage", DatasetBundle.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "filterPage", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "iterator", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "iterator", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "stream", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "stream", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "chart", List.class, Class.class, ChartSpec.class);
        requirePublicMethod(SqlLikeQuery.class, "chart", List.class, JoinBindings.class, Class.class, ChartSpec.class);
        requirePublicMethod(SqlLikeQuery.class, "schema", Class.class);
        requirePublicMethod(SqlLikeQuery.class, "exposurePolicy", QueryExposurePolicy.class);
        requirePublicMethod(SqlLikeQuery.class, "exposurePolicy");
        requirePublicMethod(SqlLikeQuery.class, "executionGuard", QueryExecutionGuard.class);
        requirePublicMethod(SqlLikeQuery.class, "executionGuard");
        requirePublicMethod(SqlLikeQuery.class, "diagnostics");
        requirePublicMethod(SqlLikeQuery.class, "diagnostics", Class.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "diagnostics", Class.class, Class.class, JoinBindings.class);
        requirePublicMethod(SqlLikeQuery.class, "planPreview");
        requirePublicMethod(SqlLikeQuery.class, "pushdownPreview");
        requirePublicMethod(SqlLikeQuery.class, "pushdownRequest");
        requirePublicMethod(SqlLikeQuery.class, "filterWithPushdown", SqlLikePushdownAdapter.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "filterWithPushdown", SqlLikePushdownAdapter.class, Class.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "explain", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "explain", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "sort");

        requirePublicMethod(SqlLikeBoundQuery.class, "filter");
        requirePublicMethod(SqlLikeBoundQuery.class, "iterator");
        requirePublicMethod(SqlLikeBoundQuery.class, "stream");
        requirePublicMethod(SqlLikeBoundQuery.class, "chart", ChartSpec.class);

        requirePublicStaticMethod(SqlLikeTemplate.class, "of", String.class, String[].class);
        requirePublicMethod(SqlLikeTemplate.class, "bind", Map.class);
        requirePublicMethod(SqlLikeTemplate.class, "bind", SqlParams.class);
        requirePublicMethod(SqlLikeTemplate.class, "source");
        requirePublicMethod(SqlLikeTemplate.class, "expectedParams");

        requirePublicStaticMethod(SqlParams.class, "builder");
        requirePublicStaticMethod(SqlParams.class, "empty");
        requirePublicMethod(SqlParams.class, "asMap");

        requirePublicMethod(QueryDiagnostics.class, "valid");
        requirePublicMethod(QueryDiagnostics.class, "errors");
        requirePublicMethod(QueryDiagnostics.class, "lintWarnings");
        requirePublicMethod(QueryDiagnostics.class, "requiredParams");
        requirePublicMethod(QueryDiagnostics.class, "referencedFields");
        requirePublicMethod(QueryDiagnostics.class, "outputFields");
        requirePublicMethod(QueryDiagnostics.class, "joinSources");
        requirePublicMethod(QueryDiagnostics.class, "hasSubqueries");
        requirePublicMethod(QueryDiagnosticsError.class, "code");
        requirePublicMethod(QueryDiagnosticsError.class, "message");
        requirePublicStaticMethod(QueryExposurePolicy.class, "unrestricted");
        requirePublicStaticMethod(QueryExposurePolicy.class, "builder");
        requirePublicMethod(QueryExposurePolicy.class, "toBuilder");
        requirePublicMethod(QueryExposurePolicy.class, "allowedFields");
        requirePublicMethod(QueryExposurePolicy.class, "allowedSources");
        requirePublicMethod(QueryExposurePolicy.class, "restrictsFields");
        requirePublicMethod(QueryExposurePolicy.class, "restrictsSources");
        requirePublicMethod(QueryExposurePolicy.class, "allowsField", String.class);
        requirePublicMethod(QueryExposurePolicy.class, "allowsSource", String.class);
        requirePublicMethod(PageResult.class, "rows");
        requirePublicMethod(PageResult.class, "hasMore");
        requirePublicMethod(PageResult.class, "nextCursor");

        requirePublicMethod(SqlLikePlanPreview.class, "source");
        requirePublicMethod(SqlLikePlanPreview.class, "isWildcard");
        requirePublicMethod(SqlLikePlanPreview.class, "selectFields");
        requirePublicMethod(SqlLikePlanPreview.class, "filters");
        requirePublicMethod(SqlLikePlanPreview.class, "filterExpression");
        requirePublicMethod(SqlLikePlanPreview.class, "groupByFields");
        requirePublicMethod(SqlLikePlanPreview.class, "havingFilters");
        requirePublicMethod(SqlLikePlanPreview.class, "havingExpression");
        requirePublicMethod(SqlLikePlanPreview.class, "qualifyFilters");
        requirePublicMethod(SqlLikePlanPreview.class, "qualifyExpression");
        requirePublicMethod(SqlLikePlanPreview.class, "orderFields");
        requirePublicMethod(SqlLikePlanPreview.class, "joins");
        requirePublicMethod(SqlLikePlanPreview.class, "paging");
        requirePublicMethod(SqlLikePlanPreview.class, "requiredParams");
        requirePublicMethod(SqlLikePlanPreview.class, "hasSubqueries");
        requirePublicMethod(SqlLikePlanPreview.class, "hasGrouping");
        requirePublicMethod(SqlLikePlanPreview.class, "hasJoins");
        requirePublicMethod(SqlLikePlanPreview.class, "hasWindows");
        requirePublicMethod(SqlLikePlanPreview.class, "hasPaging");
        requirePublicMethod(SqlLikePlanPreview.class, "hasAggregation");
        requirePublicMethod(PlanPreviewField.class, "field");
        requirePublicMethod(PlanPreviewField.class, "outputName");
        requirePublicMethod(PlanPreviewField.class, "alias");
        requirePublicMethod(PlanPreviewField.class, "metric");
        requirePublicMethod(PlanPreviewField.class, "timeBucket");
        requirePublicMethod(PlanPreviewField.class, "windowFunction");
        requirePublicMethod(PlanPreviewField.class, "windowPartitionFields");
        requirePublicMethod(PlanPreviewField.class, "windowOrderFields");
        requirePublicMethod(PlanPreviewField.class, "windowFrame");
        requirePublicMethod(PlanPreviewField.class, "isComputed");
        requirePublicMethod(PlanPreviewField.class, "isCountAll");
        requirePublicMethod(PlanPreviewField.class, "isWindow");
        requirePublicMethod(PlanPreviewField.class, "isMetric");
        requirePublicMethod(PlanPreviewField.class, "isTimeBucket");
        requirePublicMethod(PlanPreviewFilter.class, "field");
        requirePublicMethod(PlanPreviewFilter.class, "operator");
        requirePublicMethod(PlanPreviewFilter.class, "valueKind");
        requirePublicMethod(PlanPreviewFilter.class, "parameterName");
        requirePublicMethod(PlanPreviewFilter.class, "subqueryPreview");
        requirePublicMethod(PlanPreviewPredicate.class, "isLeaf");
        requirePublicMethod(PlanPreviewPredicate.class, "filter");
        requirePublicMethod(PlanPreviewPredicate.class, "operator");
        requirePublicMethod(PlanPreviewPredicate.class, "children");
        requirePublicMethod(PlanPreviewJoin.class, "type");
        requirePublicMethod(PlanPreviewJoin.class, "source");
        requirePublicMethod(PlanPreviewJoin.class, "parentField");
        requirePublicMethod(PlanPreviewJoin.class, "childField");
        requirePublicMethod(PlanPreviewOrder.class, "field");
        requirePublicMethod(PlanPreviewOrder.class, "direction");
        requirePublicMethod(PlanPreviewPaging.class, "limit");
        requirePublicMethod(PlanPreviewPaging.class, "limitParameter");
        requirePublicMethod(PlanPreviewPaging.class, "offset");
        requirePublicMethod(PlanPreviewPaging.class, "offsetParameter");
        requirePublicMethod(PlanPreviewPaging.class, "hasLimit");
        requirePublicMethod(PlanPreviewPaging.class, "hasOffset");
        requirePublicMethod(SqlLikePushdownPreview.class, "source");
        requirePublicMethod(SqlLikePushdownPreview.class, "mode");
        requirePublicMethod(SqlLikePushdownPreview.class, "pushableStages");
        requirePublicMethod(SqlLikePushdownPreview.class, "inMemoryStages");
        requirePublicMethod(SqlLikePushdownPreview.class, "fallbackReasons");
        requirePublicMethod(SqlLikePushdownPreview.class, "isFullyPushable");
        requirePublicMethod(SqlLikePushdownPreview.class, "requiresSplitExecution");
        requirePublicMethod(SqlLikePushdownPreview.class, "isInMemoryOnly");
        assertEquals(SqlLikePushdownMode.FULL, SqlLikePushdownMode.valueOf("FULL"));
        assertEquals(SqlLikePushdownMode.SPLIT, SqlLikePushdownMode.valueOf("SPLIT"));
        assertEquals(SqlLikePushdownMode.IN_MEMORY_ONLY, SqlLikePushdownMode.valueOf("IN_MEMORY_ONLY"));
        requirePublicMethod(SqlLikePushdownAdapter.class, "fetch", SqlLikePushdownRequest.class, Class.class);
        requirePublicMethod(SqlLikePushdownRequest.class, "source");
        requirePublicMethod(SqlLikePushdownRequest.class, "queryText");
        requirePublicMethod(SqlLikePushdownRequest.class, "preview");
        requirePublicMethod(SqlLikePushdownRequest.class, "requestedStages");
        requirePublicMethod(SqlLikePushdownRequest.class, "requestsStage", String.class);
        requirePublicStaticMethod(SqlLikePushdownResult.class, "of", List.class);
        requirePublicStaticMethod(SqlLikePushdownResult.class, "of", List.class, java.util.Collection.class);
        requirePublicStaticMethod(SqlLikePushdownResult.class, "of", List.class, java.util.Collection.class, int.class, Map.class);
        requirePublicMethod(SqlLikePushdownResult.class, "rows");
        requirePublicMethod(SqlLikePushdownResult.class, "pushedStages");
        requirePublicMethod(SqlLikePushdownResult.class, "sourceRowCount");
        requirePublicMethod(SqlLikePushdownResult.class, "metadata");
        requirePublicConstructor(SqlLikePushdownException.class, String.class);
        requirePublicConstructor(SqlLikePushdownException.class, String.class, Throwable.class);
        requirePublicStaticMethod(SqlLikeResultSetAdapter.class, "read", java.sql.ResultSet.class, Class.class);
        requirePublicStaticMethod(SqlLikeResultSetAdapter.class, "readPushed",
                java.sql.ResultSet.class, Class.class, java.util.Collection.class);
    }

    @Test
    public void stableNaturalContractsShouldRemainAvailable() throws Exception {
        requirePublicMethod(NaturalRuntime.class, "parse", String.class);
        requirePublicMethod(NaturalRuntime.class, "template", String.class, String[].class);

        requirePublicStaticMethod(NaturalQuery.class, "of", String.class);
        requirePublicMethod(NaturalQuery.class, "source");
        requirePublicMethod(NaturalQuery.class, "equivalentSqlLike");
        requirePublicMethod(NaturalQuery.class, "params", Map.class);
        requirePublicMethod(NaturalQuery.class, "params", SqlParams.class);
        requirePublicMethod(NaturalQuery.class, "bindTyped", List.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "bindTyped", DatasetBundle.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "bindTyped", List.class, Class.class, JoinBindings.class);
        requirePublicMethod(NaturalQuery.class, "filter", List.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "filter", DatasetBundle.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "filter", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "iterator", List.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "iterator", DatasetBundle.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "iterator", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "stream", List.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "stream", DatasetBundle.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "stream", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "chart", List.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "chart", DatasetBundle.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "chart", List.class, Class.class, ChartSpec.class);
        requirePublicMethod(NaturalQuery.class, "chart", DatasetBundle.class, Class.class, ChartSpec.class);
        requirePublicMethod(NaturalQuery.class, "chart", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "chart", List.class, JoinBindings.class, Class.class, ChartSpec.class);
        requirePublicMethod(NaturalQuery.class, "schema", Class.class);
        requirePublicMethod(NaturalQuery.class, "exposurePolicy", QueryExposurePolicy.class);
        requirePublicMethod(NaturalQuery.class, "exposurePolicy");
        requirePublicMethod(NaturalQuery.class, "executionGuard", QueryExecutionGuard.class);
        requirePublicMethod(NaturalQuery.class, "executionGuard");
        requirePublicMethod(NaturalQuery.class, "schema", List.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "schema", DatasetBundle.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "schema", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "diagnostics");
        requirePublicMethod(NaturalQuery.class, "diagnostics", Class.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "explain", List.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "explain", DatasetBundle.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "explain", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(NaturalQuery.class, "sort");

        requirePublicMethod(NaturalBoundQuery.class, "filter");
        requirePublicMethod(NaturalBoundQuery.class, "iterator");
        requirePublicMethod(NaturalBoundQuery.class, "stream");
        requirePublicMethod(NaturalBoundQuery.class, "chart");
        requirePublicMethod(NaturalBoundQuery.class, "chart", ChartSpec.class);

        requirePublicStaticMethod(NaturalTemplate.class, "of", String.class, String[].class);
        requirePublicMethod(NaturalTemplate.class, "bind", Map.class);
        requirePublicMethod(NaturalTemplate.class, "bind", SqlParams.class);
        requirePublicMethod(NaturalTemplate.class, "source");
        requirePublicMethod(NaturalTemplate.class, "expectedParams");

        requirePublicStaticMethod(NaturalVocabulary.class, "empty");
        requirePublicStaticMethod(NaturalVocabulary.class, "builder");
    }

    @Test
    public void stableSqlLikeAndNaturalFlowsShouldExecute() {
        List<Employee> sqlRows = new PojoLensRuntime()
                .parse("where active = true order by salary desc limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), sqlRows.stream().map(row -> row.name).toList());

        List<Employee> directSqlRows = PojoLensSql
                .parse("where active = true order by salary desc limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), directSqlRows.stream().map(row -> row.name).toList());

        List<Employee> naturalRows = PojoLensNatural
                .parse("show employees where active is true sort by salary descending limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), naturalRows.stream().map(row -> row.name).toList());

        List<Employee> runtimeNaturalRows = new PojoLensRuntime()
                .natural()
                .parse("show employees where active is true sort by salary descending limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), runtimeNaturalRows.stream().map(row -> row.name).toList());
    }

    @Test
    public void stableRuntimeTemplateAndCursorFlowsShouldExecute() {
        PojoLensRuntime runtime = PojoLensRuntime.ofPreset(PojoLensRuntimePreset.DEV);
        assertTrue(runtime.isStrictParameterTypes());
        assertTrue(runtime.isLintMode());

        SqlLikeTemplate template = runtime.template(
                "where department = :dept and active = :active",
                "dept",
                "active"
        );
        List<Employee> rows = template
                .bind(SqlParams.builder().put("dept", "Engineering").put("active", true).build())
                .filter(sampleEmployees(), Employee.class);
        assertEquals(2, rows.size());

        List<Employee> naturalRows = runtime.natural()
                .template(
                        "show employees where department is :dept and active is :active sort by salary descending",
                        "dept",
                        "active"
                )
                .bind(SqlParams.builder().put("dept", "Engineering").put("active", true).build())
                .filter(sampleEmployees(), Employee.class);
        assertEquals(List.of("Cara", "Alice"), naturalRows.stream().map(row -> row.name).toList());

        SqlLikeCursor cursor = SqlLikeCursor.builder().put("salary", 120000).put("id", 1).build();
        String token = cursor.toToken();
        SqlLikeCursor decoded = SqlLikeCursor.fromToken(token);
        assertNotNull(decoded);
        assertEquals(cursor, decoded);
    }

    @Test
    public void stableSavedReportContractsShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(SavedReport.class, "sqlLike", String.class, String.class, String.class);
        requirePublicStaticMethod(SavedReport.class, "natural", String.class, String.class, String.class);
        requirePublicMethod(SavedReport.class, "id");
        requirePublicMethod(SavedReport.class, "name");
        requirePublicMethod(SavedReport.class, "version");
        requirePublicMethod(SavedReport.class, "kind");
        requirePublicMethod(SavedReport.class, "queryText");
        requirePublicMethod(SavedReport.class, "source");
        requirePublicMethod(SavedReport.class, "defaultParams");
        requirePublicMethod(SavedReport.class, "chartSpec");
        requirePublicMethod(SavedReport.class, "schema");
        requirePublicMethod(SavedReport.class, "withDefaultParam", String.class, Object.class);
        requirePublicMethod(SavedReport.class, "withDefaultParams", java.util.Map.class);
        requirePublicMethod(SavedReport.class, "withChartSpec", ChartSpec.class);
        requirePublicMethod(SavedReport.class, "withSchema", laughing.man.commits.table.TabularSchema.class);
        requirePublicMethod(SavedReport.class, "planPreview");
        requirePublicMethod(SavedReport.class, "diagnostics");
        requirePublicMethod(SavedReport.class, "toQuery");
        requirePublicMethod(SavedReport.class, "toNaturalQuery");
        requirePublicMethod(SavedReport.class, "toDefinition", Class.class);
    }

    @Test
    public void stableTabularColumnTypeNameContractShouldRemainAvailable() throws Exception {
        requirePublicMethod(TabularColumn.class, "typeName");
    }

    @Test
    public void stableTypedDslContractsShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(TypedField.class, "of", String.class, Class.class);
        requirePublicMethod(TypedField.class, "fieldName");
        requirePublicMethod(TypedField.class, "valueType");
        requirePublicMethod(TypedField.class, "eq", Object.class);
        requirePublicMethod(TypedField.class, "ne", Object.class);
        requirePublicMethod(TypedField.class, "gt", Object.class);
        requirePublicMethod(TypedField.class, "gte", Object.class);
        requirePublicMethod(TypedField.class, "lt", Object.class);
        requirePublicMethod(TypedField.class, "lte", Object.class);
        requirePublicMethod(TypedField.class, "in", java.util.Collection.class);
        requirePublicMethod(TypedField.class, "isNull");
        requirePublicMethod(TypedField.class, "isNotNull");

        requirePublicMethod(TypedPredicate.class, "operator");
        requirePublicMethod(TypedPredicate.class, "field");
        requirePublicMethod(TypedPredicate.class, "value");
        requirePublicMethod(TypedPredicate.class, "values");
        requirePublicMethod(TypedPredicate.class, "children");
        requirePublicMethod(TypedPredicate.class, "isLeaf");
        requirePublicMethod(TypedPredicate.class, "and", TypedPredicate.class);
        requirePublicMethod(TypedPredicate.class, "or", TypedPredicate.class);
        requirePublicMethod(TypedPredicate.class, "not");
        requirePublicStaticMethod(TypedPredicate.class, "allOf", TypedPredicate[].class);
        requirePublicStaticMethod(TypedPredicate.class, "anyOf", TypedPredicate[].class);

        requirePublicStaticMethod(TypedQuery.class, "from", Class.class);
        requirePublicMethod(TypedQuery.class, "select", TypedField[].class);
        requirePublicMethod(TypedQuery.class, "where", TypedPredicate.class);
        requirePublicMethod(TypedQuery.class, "orderBy", TypedField.class);
        requirePublicMethod(TypedQuery.class, "orderByDesc", TypedField.class);
        requirePublicMethod(TypedQuery.class, "limit", int.class);
        requirePublicMethod(TypedQuery.class, "offset", int.class);
        requirePublicMethod(TypedQuery.class, "executionGuard", QueryExecutionGuard.class);
        requirePublicMethod(TypedQuery.class, "filter", List.class);
        requirePublicMethod(TypedQuery.class, "filter", List.class, Class.class);
        requirePublicMethod(TypedQuery.class, "explain", List.class);
        requirePublicMethod(TypedQuery.class, "schema", List.class);
        requirePublicMethod(TypedQuery.class, "schema", List.class, Class.class);

        requirePublicStaticMethod(FieldMetamodelGenerator.class, "generateTyped", Class.class);
        requirePublicStaticMethod(FieldMetamodelGenerator.class, "generateTyped",
                Class.class, String.class, String.class);
    }

    @Test
    public void stableQueryExecutionGuardContractsShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(QueryCancellationToken.class, "ofAtomic", AtomicBoolean.class);
        requirePublicStaticMethod(QueryCancellationToken.class, "ofThread", Thread.class);
        requirePublicMethod(QueryCancellationToken.class, "isCancelled");

        requirePublicStaticMethod(QueryExecutionGuard.class, "unrestricted");
        requirePublicStaticMethod(QueryExecutionGuard.class, "builder");
        requirePublicMethod(QueryExecutionGuard.class, "isUnrestricted");
        requirePublicMethod(QueryExecutionGuard.class, "hasPreExecutionLimits");
        requirePublicMethod(QueryExecutionGuard.class, "maxRowsScanned");
        requirePublicMethod(QueryExecutionGuard.class, "maxRowsReturned");
        requirePublicMethod(QueryExecutionGuard.class, "maxComplexityScore");
        requirePublicMethod(QueryExecutionGuard.class, "maxDurationMillis");
        requirePublicMethod(QueryExecutionGuard.class, "cancellationToken");
        requirePublicMethod(QueryExecutionGuard.class, "checkPreExecution", SqlLikePlanPreview.class, int.class);
        requirePublicMethod(QueryExecutionGuard.class, "checkPostExecution", int.class, long.class);
        requirePublicMethod(QueryExecutionGuard.class, "checkCancellation", int.class);
        requirePublicMethod(QueryExecutionGuard.Builder.class, "cancellationToken", QueryCancellationToken.class);

        requirePublicStaticMethod(QueryGuardOutcome.class, "allowed", QueryComplexitySummary.class);
        requirePublicStaticMethod(QueryGuardOutcome.class, "blocked",
                String.class, String.class, QueryComplexitySummary.class);
        requirePublicStaticMethod(QueryGuardOutcome.class, "cancelled",
                String.class, String.class, int.class, QueryComplexitySummary.class);
        requirePublicMethod(QueryGuardOutcome.class, "allowed");
        requirePublicMethod(QueryGuardOutcome.class, "blocked");
        requirePublicMethod(QueryGuardOutcome.class, "blockCode");
        requirePublicMethod(QueryGuardOutcome.class, "blockReason");
        requirePublicMethod(QueryGuardOutcome.class, "complexitySummary");
        requirePublicMethod(QueryGuardOutcome.class, "rowsReturnedBeforeAbort");
        requirePublicMethod(QueryGuardOutcome.class, "auditMetadata");

        requirePublicStaticMethod(QueryComplexitySummary.class, "from", SqlLikePlanPreview.class);
        requirePublicMethod(QueryComplexitySummary.class, "filterCount");
        requirePublicMethod(QueryComplexitySummary.class, "joinCount");
        requirePublicMethod(QueryComplexitySummary.class, "hasGrouping");
        requirePublicMethod(QueryComplexitySummary.class, "hasAggregation");
        requirePublicMethod(QueryComplexitySummary.class, "hasWindows");
        requirePublicMethod(QueryComplexitySummary.class, "hasSubqueries");
        requirePublicMethod(QueryComplexitySummary.class, "hasPaging");
        requirePublicMethod(QueryComplexitySummary.class, "estimatedComplexityScore");

        requirePublicStaticMethod(QueryExecutionGuardException.class, "of", QueryGuardOutcome.class);
        requirePublicMethod(QueryExecutionGuardException.class, "outcome");
    }

    @Test
    public void queryCancellationGuardShouldBeUsableFromPublicApi() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryCancellationToken token = QueryCancellationToken.ofAtomic(cancel);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(token)
                .build();

        assertEquals(token, guard.cancellationToken());
        assertTrue(!guard.isUnrestricted());
        assertTrue(guard.checkCancellation(0).allowed());

        cancel.set(true);
        QueryGuardOutcome outcome = guard.checkCancellation(3);

        assertEquals("GUARD_CANCELLED", outcome.blockCode());
        assertEquals(3, outcome.rowsReturnedBeforeAbort());
    }

    private static Method requirePublicMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                () -> "Expected public method: " + type.getSimpleName() + "." + name);
        return method;
    }

    private static Method requirePublicStaticMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = requirePublicMethod(type, name, parameterTypes);
        assertTrue(Modifier.isStatic(method.getModifiers()),
                () -> "Expected static method: " + type.getSimpleName() + "." + name);
        return method;
    }

    private static Constructor<?> requirePublicConstructor(Class<?> type, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Constructor<?> constructor = type.getConstructor(parameterTypes);
        assertTrue(Modifier.isPublic(constructor.getModifiers()),
                () -> "Expected public constructor: " + type.getSimpleName());
        return constructor;
    }
}


