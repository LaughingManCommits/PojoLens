package laughing.man.commits;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.builder.QueryWindowOrder;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.snapshot.SnapshotComparison;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.metamodel.FieldMetamodel;
import laughing.man.commits.metamodel.FieldMetamodelGenerator;
import laughing.man.commits.sqllike.SqlLikeLintCodes;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.SqlLikeTemplate;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.testing.FluentSqlLikeParity;
import laughing.man.commits.testing.QueryRegressionFixture;
import laughing.man.commits.testing.QuerySnapshotFixture;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PublicApiCoverageTest {

    @BeforeEach
    public void setUp() {
        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        PojoLens.setSqlLikeCacheMaxEntries(256);
        PojoLens.setSqlLikeCacheMaxWeight(0L);
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();
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
    }

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

    @Test
    public void sqlLikeQueryOfShouldNormalizeAndExposeSource() {
        SqlLikeQuery query = SqlLikeQuery.of("  where integerField >= 2  ");
        assertEquals("where integerField >= 2", query.source());
    }

    @Test
    public void sqlLikeQuerySortShouldReturnNullWithoutOrderAndDirectionWithOrder() {
        assertNull(SqlLikeQuery.of("where integerField >= 1").sort());
        assertEquals(Sort.DESC, SqlLikeQuery.of("where integerField >= 1 order by integerField desc").sort());
    }

    @Test
    public void sqlLikeQueryBindTypedWithJoinSourcesShouldReturnExecutableRows() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("employees", employees);

        List<Company> rows = SqlLikeQuery
                .of("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .bindTyped(companies, Company.class, joinSources)
                .filter();

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void sqlLikeTemplateFactoryAndSqlParamsShouldBeUsableFromPublicApi() {
        SqlLikeTemplate template = PojoLens.template(
                "where department = :dept and active = :active",
                "dept",
                "active"
        );
        List<Employee> rows = template
                .bind(SqlParams.builder().put("dept", "Engineering").put("active", true).build())
                .filter(sampleEmployees(), Employee.class);

        assertEquals(2, rows.size());
        assertEquals(Arrays.asList("Alice", "Cara"),
                rows.stream().map(r -> r.name).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void strictParameterTypingControlsShouldBeUsableFromPublicApi() {
        PojoLensRuntime runtime = PojoLens.newRuntime();
        assertFalse(runtime.isStrictParameterTypes());

        runtime.setStrictParameterTypes(true);
        assertTrue(runtime.isStrictParameterTypes());

        SqlLikeQuery strictQuery = runtime.parse("where salary >= :minSalary");
        assertTrue(strictQuery.isStrictParameterTypesEnabled());
        assertFalse(strictQuery.strictParameterTypes(false).isStrictParameterTypesEnabled());
        assertTrue(PojoLens.parse("where salary >= :minSalary").strictParameterTypes().isStrictParameterTypesEnabled());
    }

    @Test
    public void keysetCursorControlsShouldBeUsableFromPublicApi() {
        SqlLikeCursor cursor = PojoLens.newKeysetCursorBuilder()
                .put("salary", 120000)
                .put("id", 1)
                .build();

        String token = cursor.toToken();
        SqlLikeCursor decoded = PojoLens.parseKeysetCursor(token);
        assertEquals(cursor, decoded);

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc, id desc limit 20")
                .keysetAfter(decoded)
                .filter(sampleEmployees(), Employee.class);

        assertEquals(1, rows.size());
        assertEquals("Bob", rows.get(0).name);
    }

    @Test
    public void streamingControlsShouldBeUsableFromPublicApi() {
        List<String> fluentNames = PojoLens.newQueryBuilder(sampleEmployees())
                .addRule("active", true, Clauses.EQUAL)
                .limit(2)
                .initFilter()
                .stream(Employee.class)
                .map(row -> row.name)
                .toList();
        assertEquals(List.of("Alice", "Bob"), fluentNames);

        List<String> sqlNames = PojoLens
                .parse("where active = true limit 2")
                .stream(sampleEmployees(), Employee.class)
                .map(row -> row.name)
                .toList();
        assertEquals(List.of("Alice", "Bob"), sqlNames);

        List<String> sqlBoundNames = PojoLens
                .parse("where active = true limit 2")
                .bindTyped(sampleEmployees(), Employee.class)
                .stream()
                .map(row -> row.name)
                .toList();
        assertEquals(List.of("Alice", "Bob"), sqlBoundNames);
    }

    @Test
    public void optionalIndexControlsShouldBeUsableFromPublicApi() {
        List<Employee> baseline = PojoLens.newQueryBuilder(sampleEmployees())
                .addRule("department", "Engineering", Clauses.EQUAL)
                .addRule("active", true, Clauses.EQUAL)
                .initFilter()
                .filter(Employee.class);

        List<Employee> indexed = PojoLens.newQueryBuilder(sampleEmployees())
                .addIndex("department")
                .addIndex("active")
                .addRule("department", "Engineering", Clauses.EQUAL)
                .addRule("active", true, Clauses.EQUAL)
                .initFilter()
                .filter(Employee.class);

        assertEquals(
                baseline.stream().map(row -> row.id).toList(),
                indexed.stream().map(row -> row.id).toList()
        );
        assertEquals(
                List.of("department", "active"),
                PojoLens.newQueryBuilder(sampleEmployees())
                        .addIndex("department")
                        .addIndex("active")
                        .explain()
                        .get("indexes")
        );

        Object typedIndexes = PojoLens.newQueryBuilder(Arrays.asList(
                        new Foo("a", new Date(), 1),
                        new Foo("b", new Date(), 2)))
                .addIndex(Foo::getStringField)
                .explain()
                .get("indexes");
        assertEquals(List.of("stringField"), typedIndexes);
    }

    @Test
    public void fluentWindowAndQualifyControlsShouldBeUsableFromPublicApi() {
        List<WindowRankRow> rows = PojoLens.newQueryBuilder(sampleEmployees())
                .addRule("active", true, Clauses.EQUAL)
                .addWindow(
                        "rn",
                        WindowFunction.ROW_NUMBER,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("salary", Sort.DESC))
                )
                .addQualify("rn", 1, Clauses.SMALLER_EQUAL)
                .addOrder("department", 1)
                .initFilter()
                .filter(Sort.ASC, WindowRankRow.class);

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(1L, rows.get(0).rn);
    }

    @Test
    public void lintControlsShouldBeUsableFromPublicApi() {
        PojoLensRuntime runtime = PojoLens.newRuntime();
        assertFalse(runtime.isLintMode());

        runtime.setLintMode(true);
        assertTrue(runtime.isLintMode());

        SqlLikeQuery lintQuery = runtime.parse("select * from companies limit 1");
        assertTrue(lintQuery.isLintModeEnabled());
        assertEquals(1, lintQuery.suppressLintWarnings(SqlLikeLintCodes.SELECT_WILDCARD).lintWarnings().size());
        assertFalse(PojoLens.parse("select * from companies limit 1").lintMode(false).isLintModeEnabled());
    }

    @Test
    public void runtimePolicyPresetsShouldBeUsableFromPublicApi() {
        PojoLensRuntime devRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);
        assertTrue(devRuntime.isStrictParameterTypes());
        assertTrue(devRuntime.isLintMode());

        PojoLensRuntime prodRuntime = PojoLens.newRuntime().applyPreset(PojoLensRuntimePreset.PROD);
        assertFalse(prodRuntime.isStrictParameterTypes());
        assertFalse(prodRuntime.isLintMode());
        assertTrue(prodRuntime.sqlLikeCache().isEnabled());
    }

    @Test
    public void parityHelperShouldBeUsableFromPublicApi() {
        FluentSqlLikeParity.assertUnorderedEquals(List.of("a", "b"), List.of("b", "a"));

        QuerySnapshotFixture snapshot = QuerySnapshotFixture.of("employees", sampleEmployees());
        FluentSqlLikeParity.assertUnorderedEquals(
                snapshot,
                StatsRow.class,
                builder -> builder.addGroup("department").addCount("total"),
                PojoLens.parse("select department, count(*) as total group by department"),
                row -> row.department + ":" + row.total
        );
    }

    @Test
    public void fieldMetamodelGeneratorShouldBeUsableFromPublicApi() {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generate(Employee.class);
        assertEquals("EmployeeFields", metamodel.simpleName());
        assertTrue(metamodel.fieldNames().contains("department"));
        assertEquals("department", metamodel.constants().get("DEPARTMENT"));
    }

    @Test
    public void chartQueryPresetsShouldBeUsableFromPublicApi() {
        ChartQueryPreset<StatsRow> preset = ChartQueryPresets
                .categoryCounts("department", "total", StatsRow.class);

        assertEquals(ChartType.BAR, preset.chartSpec().type());
        assertTrue(preset.source().contains("count(*) as total"));
        assertEquals(2, preset.rows(sampleEmployees()).size());
    }

    @Test
    public void reportDefinitionsShouldBeUsableFromPublicApi() {
        ReportDefinition<StatsRow> sqlReport = PojoLens.report(
                PojoLens.parse("select department, count(*) as total group by department order by department asc"),
                StatsRow.class
        );
        assertEquals(2, sqlReport.rows(sampleEmployees()).size());

        ReportDefinition<StatsRow> fluentReport = PojoLens.report(
                StatsRow.class,
                builder -> builder.addGroup("department").addCount("total")
        );
        assertEquals(2, fluentReport.rows(sampleEmployees()).size());
        assertEquals("fluent", fluentReport.source());
    }

    @Test
    public void datasetBundleFactoriesShouldBeUsableFromPublicApi() {
        DatasetBundle bundle = PojoLens.bundle(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );

        List<Company> rows = PojoLens
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .filter(bundle, Company.class);

        assertEquals(1, rows.size());
        assertTrue(bundle.hasJoinSources());
        assertEquals(1, bundle.joinSourceCount());
    }

    @Test
    public void telemetryHooksShouldBeUsableFromPublicApi() {
        List<QueryTelemetryEvent> events = new ArrayList<>();

        PojoLensRuntime runtime = PojoLens.newRuntime();
        runtime.setTelemetryListener(events::add);
        runtime.parse("where salary >= 100000").filter(sampleEmployees(), Employee.class);
        assertFalse(events.isEmpty());

        events.clear();
        PojoLens.newQueryBuilder(sampleEmployees())
                .telemetry(events::add)
                .addRule("active", true, Clauses.EQUAL)
                .initFilter()
                .filter(Employee.class);
        assertFalse(events.isEmpty());
    }

    @Test
    public void computedFieldRegistryShouldBeUsableFromPublicApi() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build();

        List<ComputedSalaryRow> rows = PojoLens
                .parse("select name, adjustedSalary where adjustedSalary >= 120000 order by adjustedSalary desc")
                .computedFields(registry)
                .filter(sampleEmployees(), ComputedSalaryRow.class);

        PojoLensRuntime runtime = PojoLens.newRuntime();
        runtime.setComputedFieldRegistry(registry);
        assertEquals("adjustedSalary", runtime.getComputedFieldRegistry().get("adjustedSalary").name());
        assertEquals(3, rows.size());
    }

    @Test
    public void snapshotComparisonShouldBeUsableFromPublicApi() {
        List<Employee> employees = sampleEmployees();
        SnapshotComparison<Employee, Integer> comparison = PojoLens
                .compareSnapshots(employees, employees)
                .byKey(employee -> employee.id);

        assertEquals(4, comparison.rows().size());
        assertEquals(4, comparison.summary().unchangedCount());
    }

    @Test
    public void timeBucketPresetShouldBeUsableFromPublicApi() {
        TimeBucketPreset preset = TimeBucketPreset.week()
                .withZone("Europe/Amsterdam")
                .withWeekStart("sunday");

        QueryBuilder builder = PojoLens.newQueryBuilder(sampleEmployees())
                .addTimeBucket("hireDate", preset, "period")
                .addCount("total");

        assertTrue(builder.explain().get("timeBuckets").toString().contains("Europe/Amsterdam"));
        assertEquals("WEEK", preset.bucket().name());
    }

    @Test
    public void tabularSchemaMetadataShouldBeUsableFromPublicApi() {
        TabularSchema fluentSchema = PojoLens.newQueryBuilder(sampleEmployees())
                .addGroup("department")
                .addCount("total")
                .schema(StatsRow.class);

        TabularSchema sqlSchema = PojoLens
                .parse("select department, count(*) as total group by department")
                .schema(StatsRow.class);

        ReportDefinition<StatsRow> report = PojoLens.report(
                PojoLens.parse("select department, count(*) as total group by department"),
                StatsRow.class
        );

        assertEquals(List.of("department", "total"), fluentSchema.names());
        assertEquals(List.of("department", "total"), sqlSchema.names());
        assertEquals(List.of("department", "total"), report.schema().names());
        assertEquals("metric:COUNT", report.schema().column("total").formatHint());
    }

    @Test
    public void regressionFixturesShouldBeUsableFromPublicApi() {
        QuerySnapshotFixture snapshot = QuerySnapshotFixture.of("employees-regression", sampleEmployees());

        QueryRegressionFixture<StatsRow> fixture = QueryRegressionFixture.sql(
                snapshot,
                PojoLens.parse("select department, count(*) as total group by department order by department asc"),
                StatsRow.class
        );

        fixture
                .assertRowCount(2)
                .assertOrderedRows(row -> row.department + ":" + row.total,
                        "Engineering:3",
                        "Finance:1");
    }

    @Test
    public void queryBuilderLimitShouldValidateAndApply() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("a", new Date(), 1),
                new Foo("b", new Date(), 2)
        );

        try {
            PojoLens.newQueryBuilder(source).limit(-1);
            fail("Expected IllegalArgumentException for negative limit");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("maxRows must be >= 0"));
        }

        try {
            PojoLens.newQueryBuilder(source).offset(-1);
            fail("Expected IllegalArgumentException for negative offset");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("rowOffset must be >= 0"));
        }

        List<Foo> empty = PojoLens.newQueryBuilder(source)
                .limit(0)
                .initFilter()
                .filter(Foo.class);
        assertEquals(0, empty.size());

        List<Foo> offsetRows = PojoLens.newQueryBuilder(source)
                .offset(1)
                .initFilter()
                .filter(Foo.class);
        assertEquals(1, offsetRows.size());
        assertEquals("b", offsetRows.get(0).getStringField());
    }

    @Test
    public void copyOnBuildFlagShouldControlSnapshotBehavior() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("a", new Date(), 1),
                new Foo("b", new Date(), 2)
        );

        QueryBuilder isolated = PojoLens.newQueryBuilder(source)
                .addRule("stringField", "a", Clauses.EQUAL, Separator.OR)
                .copyOnBuild(true);
        Filter isolatedFilter = isolated.initFilter();
        isolated.addRule("stringField", "b", Clauses.EQUAL, Separator.OR);
        List<Foo> isolatedRows = isolatedFilter.filter(Foo.class);
        assertEquals(1, isolatedRows.size());
        assertEquals("a", isolatedRows.get(0).getStringField());
        List<Foo> isolatedRowsAgain = isolatedFilter.filter(Foo.class);
        assertEquals(1, isolatedRowsAgain.size());
        assertEquals("a", isolatedRowsAgain.get(0).getStringField());

        QueryBuilder shared = PojoLens.newQueryBuilder(source)
                .addRule("stringField", "a", Clauses.EQUAL, Separator.OR)
                .copyOnBuild(false);
        Filter sharedFilter = shared.initFilter();
        shared.addRule("stringField", "b", Clauses.EQUAL, Separator.OR);
        List<Foo> sharedRows = sharedFilter.filter(Foo.class);
        assertEquals(2, sharedRows.size());
        assertEquals(2, sharedFilter.filter(Foo.class).size());
    }

    @Test
    public void typedOverloadsShouldSupportDistinctRuleDateFormatHavingAndJoin() {
        Date now = new Date();
        List<Foo> source = Arrays.asList(
                new Foo("a", now, 10),
                new Foo("a", now, 20),
                new Foo("b", now, 30)
        );

        List<Foo> distinctRows = PojoLens.newQueryBuilder(source)
                .addDistinct(Foo::getStringField)
                .addRule(Foo::getDateField, now, Clauses.EQUAL, Separator.AND, PojoLens.SDF)
                .initFilter()
                .filter(Foo.class);
        assertEquals(2, distinctRows.size());

        List<DepartmentMetricInput> metrics = Arrays.asList(
                new DepartmentMetricInput("eng", 100),
                new DepartmentMetricInput("eng", 50),
                new DepartmentMetricInput("sales", 30)
        );

        List<DepartmentMetricResult> grouped = PojoLens.newQueryBuilder(metrics)
                .addGroup(DepartmentMetricInput::getDepartment)
                .addMetric(DepartmentMetricInput::getAmount, Metric.SUM, "totalAmount")
                .addHaving(DepartmentMetricResult::getTotalAmount, 120, Clauses.BIGGER_EQUAL, Separator.AND, null)
                .initFilter()
                .filter(DepartmentMetricResult.class);
        assertEquals(1, grouped.size());
        assertEquals("eng", grouped.get(0).department);

        List<JoinParent> parents = Arrays.asList(
                new JoinParent(1, "p1"),
                new JoinParent(2, "p2")
        );
        List<JoinChild> children = Collections.singletonList(new JoinChild(1, "c1"));

        List<JoinProjection> joined = PojoLens.newQueryBuilder(parents)
                .addJoinBeans(JoinParent::getId, children, JoinChild::getParentId, Join.LEFT_JOIN)
                .initFilter()
                .join()
                .filter(JoinProjection.class);
        assertEquals(2, joined.size());
        assertEquals("c1", joined.get(0).tag);
    }

    public static class StatsRow {
        public String department;
        public long total;

        public StatsRow() {
        }
    }

    public static class DepartmentMetricInput {
        public String department;
        public int amount;

        public DepartmentMetricInput() {
        }

        public DepartmentMetricInput(String department, int amount) {
            this.department = department;
            this.amount = amount;
        }

        public String getDepartment() {
            return department;
        }

        public int getAmount() {
            return amount;
        }
    }

    public static class DepartmentMetricResult {
        public String department;
        public long totalAmount;

        public DepartmentMetricResult() {
        }

        public String getDepartment() {
            return department;
        }

        public long getTotalAmount() {
            return totalAmount;
        }
    }

    public static class JoinParent {
        public int id;
        public String name;

        public JoinParent() {
        }

        public JoinParent(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }
    }

    public static class JoinChild {
        public int parentId;
        public String tag;

        public JoinChild() {
        }

        public JoinChild(int parentId, String tag) {
            this.parentId = parentId;
            this.tag = tag;
        }

        public int getParentId() {
            return parentId;
        }
    }

    public static class JoinProjection {
        public int id;
        public String name;
        public int parentId;
        public String tag;

        public JoinProjection() {
        }
    }

    public static class WindowRankRow {
        public String department;
        public String name;
        public long rn;

        public WindowRankRow() {
        }
    }

    public static class ComputedSalaryRow {
        public String name;
        public double adjustedSalary;

        public ComputedSalaryRow() {
        }
    }

}
