package laughing.man.commits.natural;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import laughing.man.commits.testutil.TimeBucketTestFixtures.DepartmentPeriodAgg;
import laughing.man.commits.testutil.WindowTestFixtures.DepartmentRank;
import laughing.man.commits.testutil.WindowTestFixtures.WindowMetricProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.TimeBucketTestFixtures.sampleRows;
import static laughing.man.commits.testutil.WindowTestFixtures.sampleWindowMetricInputs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NaturalQueryContractTest {

    @Test
    public void shouldExecuteWildcardFilterSortAndLimit() {
        List<Employee> rows = PojoLensNatural
                .parse("show employees where active is true sort by salary descending limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void fillerWordsShouldRemainOptionalForExecutionAndExplain() {
        NaturalQuery query = PojoLensNatural.parse(
                "show me the employees where the active is true sort by the salary descending limit 2"
        );

        List<Employee> rows = query.filter(sampleEmployees(), Employee.class);
        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());

        Map<String, Object> explain = query.explain(sampleEmployees(), Employee.class);
        assertEquals(
                "select * where active = true order by salary desc limit 2",
                explain.get("equivalentSqlLike")
        );
    }

    @Test
    public void connectorLeadInsAndComparisonAliasesShouldExecuteDeterministically() {
        NaturalQuery query = PojoLensNatural.parse(
                "show employees who are active and salary greater than or equal to 120000 "
                        + "ordered by salary in descending order limit 2"
        );

        List<Employee> rows = query.filter(sampleEmployees(), Employee.class);
        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());

        Map<String, Object> explain = query.explain(sampleEmployees(), Employee.class);
        assertEquals(
                "select * where (active = true and salary >= 120000) order by salary desc limit 2",
                explain.get("equivalentSqlLike")
        );
    }

    @Test
    public void connectorLeadInsShouldSupportNegatedBooleanShorthand() {
        List<Employee> rows = PojoLensNatural.parse("show employees who are not active ordered by salary in ascending order")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Dan"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void inflectedOperatorAliasesShouldExecuteDeterministically() {
        List<Employee> rows = PojoLensNatural.parse(
                        "show employees where department containing ine "
                                + "and name starting with A and name ending with e"
                )
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Alice"), rows.stream().map(row -> row.name).toList());

        Map<String, Object> explain = PojoLensNatural.parse(
                        "show employees where department containing ine "
                                + "and name starting with A and name ending with e"
                )
                .explain(sampleEmployees(), Employee.class);
        assertEquals(
                "select * where ((department contains 'ine' and name matches '^\\QA\\E.*') and name matches '.*\\Qe\\E$')",
                explain.get("equivalentSqlLike")
        );
    }

    @Test
    public void shouldExecuteAliasedProjectionWithParameters() {
        List<EmployeeSummary> rows = PojoLensNatural
                .parse("show name as employee name, salary as annual salary "
                        + "where department is :dept and salary is at least :minSalary sort by salary ascending")
                .params(Map.of("dept", "Engineering", "minSalary", 120000))
                .filter(sampleEmployees(), EmployeeSummary.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).employeeName);
        assertEquals(120000, rows.get(0).annualSalary);
        assertEquals("Cara", rows.get(1).employeeName);
        assertEquals(130000, rows.get(1).annualSalary);
    }

    @Test
    public void explainShouldExposeNaturalTypeAndEquivalentSqlLike() {
        Map<String, Object> explain = PojoLensNatural
                .parse("show employees where active is true sort by salary descending limit 2")
                .explain(sampleEmployees(), Employee.class);

        assertEquals("natural", explain.get("type"));
        assertEquals("show employees where active is true sort by salary descending limit 2", explain.get("source"));
        assertEquals("select * where active = true order by salary desc limit 2", explain.get("normalizedQuery"));
        assertEquals(explain.get("normalizedQuery"), explain.get("equivalentSqlLike"));
    }

    @Test
    public void runtimeNaturalEntryPointShouldApplyTelemetryAndRemainChartCapable() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        NaturalQuery query = runtime.natural().parse("show salary, name where active is true sort by salary descending limit 2");

        ChartData chart = query.chart(
                sampleEmployees(),
                Employee.class,
                ChartSpec.of(ChartType.BAR, "name", "salary")
        );

        assertEquals(2, chart.getLabels().size());
        assertTrue(query.isStrictParameterTypesEnabled() == runtime.isStrictParameterTypes());
    }

    @Test
    public void shouldExecuteGroupedNaturalAggregateQueryWithHavingAndSort() {
        List<DepartmentCount> rows = PojoLensNatural
                .parse("show department, count of employees as total "
                        + "where active is true group by department having total is at least 2 sort by total descending")
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(2L, rows.get(0).total);
    }

    @Test
    public void shouldExecuteNaturalTimeBucketAggregation() {
        List<DepartmentPeriodAgg> rows = PojoLensNatural
                .parse("show department, bucket hire date by month as period, count of employees as total, "
                        + "sum of salary as payroll group by department, period sort by payroll descending")
                .filter(sampleRows(), DepartmentPeriodAgg.class);

        assertEquals(List.of(
                        "Engineering|2025-01|2|300",
                        "Engineering|2025-02|1|150",
                        "Finance|2025-02|1|300"
                ),
                normalizeDepartmentPeriodAgg(rows));
    }

    @Test
    public void shouldExecuteNaturalWindowQualifyQuery() {
        List<DepartmentRank> rows = PojoLensNatural
                .parse("show department, name, salary, "
                        + "row number by department ordered by salary descending then id ascending as rn "
                        + "where active is true qualify rn is at most 1 sort by department ascending")
                .filter(sampleEmployees(), DepartmentRank.class);

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(130000, rows.get(0).salary);
        assertEquals(1L, rows.get(0).rn);
        assertEquals("Finance", rows.get(1).department);
        assertEquals("Bob", rows.get(1).name);
        assertEquals(90000, rows.get(1).salary);
        assertEquals(1L, rows.get(1).rn);
    }

    @Test
    public void shouldExecuteRunningAggregateWindowPhrases() {
        List<WindowMetricProjection> rows = PojoLensNatural
                .parse("show department, seq, amount, "
                        + "running sum of amount by department ordered by seq ascending as running sum, "
                        + "running count of amount by department ordered by seq ascending as running count, "
                        + "running count of employees by department ordered by seq ascending as running count all, "
                        + "running average of amount by department ordered by seq ascending as running avg "
                        + "sort by department ascending, seq ascending")
                .filter(sampleWindowMetricInputs(), WindowMetricProjection.class);

        assertEquals(6, rows.size());
        assertEquals("A", rows.get(0).department);
        assertEquals(10L, rows.get(0).runningSum);
        assertEquals(1L, rows.get(0).runningCount);
        assertEquals(1L, rows.get(0).runningCountAll);
        assertEquals(10D, rows.get(0).runningAvg);

        assertEquals("A", rows.get(1).department);
        assertEquals(10L, rows.get(1).runningSum);
        assertEquals(1L, rows.get(1).runningCount);
        assertEquals(2L, rows.get(1).runningCountAll);
        assertEquals(10D, rows.get(1).runningAvg);

        assertEquals("A", rows.get(2).department);
        assertEquals(15L, rows.get(2).runningSum);
        assertEquals(2L, rows.get(2).runningCount);
        assertEquals(3L, rows.get(2).runningCountAll);
        assertEquals(7.5D, rows.get(2).runningAvg);
    }

    @Test
    public void runtimeNaturalVocabularyShouldResolveAliasesForExecutionAndExplain() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "annual pay", "pay")
                .field("department", "team")
                .build());

        NaturalQuery query = runtime.natural()
                .parse("show name, annual pay where team is Engineering sort by annual pay descending limit 2");

        List<Employee> rows = query.filter(sampleEmployees(), Employee.class);
        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
        assertEquals(List.of(130000, 120000), rows.stream().map(row -> row.salary).toList());

        Map<String, Object> explain = query.explain(sampleEmployees(), Employee.class);
        assertEquals(
                Map.of("annual pay", "salary", "team", "department"),
                explain.get("resolvedNaturalFields")
        );
        assertEquals(
                "select name, salary where department = 'Engineering' order by salary desc limit 2",
                explain.get("resolvedEquivalentSqlLike")
        );
    }

    @Test
    public void runtimeNaturalVocabularyShouldResolveGroupedAggregatesForExecutionAndExplain() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "annual pay")
                .field("department", "team")
                .build());

        NaturalQuery query = runtime.natural().parse(
                "show team as dept, sum of annual pay as total payroll, count of employees as headcount "
                        + "where active is true group by team having total payroll is at least 200000 "
                        + "sort by total payroll descending"
        );

        List<DepartmentPayrollRow> rows = query.filter(sampleEmployees(), DepartmentPayrollRow.class);
        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals(250000L, rows.get(0).totalPayroll);
        assertEquals(2L, rows.get(0).headcount);

        Map<String, Object> explain = query.explain(sampleEmployees(), DepartmentPayrollRow.class);
        assertEquals(
                Map.of("annual pay", "salary", "team", "department"),
                explain.get("resolvedNaturalFields")
        );
        assertEquals(
                "select department as dept, sum(salary) as totalPayroll, count(*) as headcount "
                        + "where active = true group by department having totalPayroll >= 200000 order by totalPayroll desc",
                explain.get("resolvedEquivalentSqlLike")
        );
    }

    @Test
    public void runtimeNaturalVocabularyShouldResolveTimeBucketFieldPhrases() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("hireDate", "start date")
                .build());

        List<PeriodPayrollRow> rows = runtime.natural()
                .parse("show bucket start date by month as period, sum of salary as payroll "
                        + "group by period sort by period ascending")
                .filter(sampleRows(), PeriodPayrollRow.class);

        assertEquals(List.of("2025-01", "2025-02"), rows.stream().map(row -> row.period).toList());
        assertEquals(List.of(300L, 450L), rows.stream().map(row -> row.payroll).toList());
    }

    @Test
    public void runtimeNaturalTemplateShouldValidateSchemaAndBindParameters() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        NaturalTemplate template = runtime.natural().template(
                "show name as employee name, salary as annual salary "
                        + "where department is :dept and salary is at least :minSalary sort by salary ascending",
                "dept",
                "minSalary"
        );

        List<EmployeeSummary> rows = template
                .bind(Map.of("dept", "Engineering", "minSalary", 120000))
                .filter(sampleEmployees(), EmployeeSummary.class);

        assertEquals(List.of("Alice", "Cara"), rows.stream().map(row -> row.employeeName).toList());
        assertEquals(
                List.of(120000, 130000),
                rows.stream().map(row -> row.annualSalary).toList()
        );
        assertThrows(IllegalArgumentException.class, () -> template.bind(Map.of("dept", "Engineering")));
        assertThrows(IllegalArgumentException.class,
                () -> template.bind(Map.of("dept", "Engineering", "minSalary", 120000, "extra", true)));
    }

    @Test
    public void runtimeNaturalComputedFieldsShouldWorkWithPlainWordingAndExplain() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setComputedFieldRegistry(ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build());

        NaturalQuery query = runtime.natural().template(
                        "show name, adjusted salary "
                                + "where adjusted salary is at least :minSalary sort by adjusted salary descending",
                        "minSalary"
                )
                .bind(Map.of("minSalary", 130000.0));

        List<ComputedNaturalSalaryRow> rows = query.filter(sampleEmployees(), ComputedNaturalSalaryRow.class);
        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
        assertEquals(143000.0, rows.get(0).adjustedSalary, 0.0001);
        assertEquals(132000.0, rows.get(1).adjustedSalary, 0.0001);

        Map<String, Object> explain = query.explain(sampleEmployees(), ComputedNaturalSalaryRow.class);
        assertTrue(((List<?>) explain.get("computedFields")).toString().contains("adjustedSalary:salary * 1.1:Double"));
        assertEquals(
                "select name, adjustedSalary where adjustedSalary >= :minSalary order by adjustedSalary desc",
                explain.get("resolvedEquivalentSqlLike")
        );
    }

    @Test
    public void runtimeNaturalVocabularyShouldResolveWindowPhrasesAndQualifyAliases() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("department", "team")
                .field("salary", "annual pay")
                .field("id", "employee id")
                .build());

        NaturalQuery query = runtime.natural().parse(
                "show team as dept, name, annual pay as pay, "
                        + "row number by team ordered by annual pay descending then employee id ascending as top rank "
                        + "where active is true qualify top rank is at most 1 sort by dept ascending"
        );

        List<NaturalWindowAliasRow> rows = query.filter(sampleEmployees(), NaturalWindowAliasRow.class);
        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(130000, rows.get(0).pay);
        assertEquals(1L, rows.get(0).topRank);
        assertEquals("Finance", rows.get(1).dept);
        assertEquals("Bob", rows.get(1).name);
        assertEquals(90000, rows.get(1).pay);
        assertEquals(1L, rows.get(1).topRank);

        Map<String, Object> explain = query.explain(sampleEmployees(), NaturalWindowAliasRow.class);
        assertEquals(
                Map.of("team", "department", "annual pay", "salary", "employee id", "id"),
                explain.get("resolvedNaturalFields")
        );
        assertEquals(
                "select department as dept, name, salary as pay, "
                        + "ROW_NUMBER() OVER (PARTITION BY department ORDER BY salary DESC, id ASC) as topRank "
                        + "where active = true qualify topRank <= 1 order by dept asc",
                explain.get("resolvedEquivalentSqlLike")
        );
    }

    @Test
    public void chartPhraseShouldInferChartSpecForExecutionAndExplain() {
        NaturalQuery query = PojoLensNatural
                .parse("show department, count of employees as total "
                        + "where active equals true grouped by department ordered by total in descending order as a bar chart");

        ChartData chart = query.chart(sampleEmployees(), DepartmentCount.class);
        assertEquals(ChartType.BAR, chart.getType());
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
        assertEquals(List.of(2d, 1d), chart.getDatasets().get(0).getValues());

        Map<String, Object> explain = query.explain(sampleEmployees(), DepartmentCount.class);
        assertEquals("BAR", explain.get("naturalChartType"));
        assertEquals(
                Map.of("type", "BAR", "xField", "department", "yField", "total"),
                explain.get("resolvedNaturalChartSpec")
        );
    }

    @Test
    public void boundNaturalChartShouldUseInferredChartSpec() {
        ChartData chart = PojoLensNatural
                .parse("show department, count of employees as total "
                        + "where active equals true grouped by department ordered by total in descending order as a bar chart")
                .bindTyped(sampleEmployees(), DepartmentCount.class)
                .chart();

        assertEquals(ChartType.BAR, chart.getType());
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
    }

    @Test
    public void exactFieldMatchShouldWinOverVocabularyAlias() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("department", "salary")
                .build());

        List<Employee> rows = runtime.natural()
                .parse("show employees where salary is at least 120000 sort by salary descending")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void ambiguousVocabularyAliasShouldFailDeterministically() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "pay")
                .field("department", "pay")
                .build());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> runtime.natural()
                        .parse("show pay")
                        .filter(sampleEmployees(), Employee.class)
        );

        assertTrue(error.getMessage().contains("Ambiguous natural field term 'pay'"));
    }

    @Test
    public void unknownNaturalFieldTermShouldFailDeterministically() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLensNatural.parse("show bonus").filter(sampleEmployees(), Employee.class)
        );

        assertTrue(error.getMessage().contains("Unknown natural field term 'bonus'"));
    }

    @Test
    public void shouldExecuteNaturalJoinQueryWithJoinBindingsAndExplain() {
        NaturalQuery query = PojoLensNatural.parse(
                "from companies as company join employees as employee "
                        + "on company id equals employee company id "
                        + "show company where employee title is Engineer"
        );

        List<Company> rows = query.filter(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees()),
                Company.class
        );

        assertEquals(List.of("Acme"), rows.stream().map(row -> row.name).toList());

        Map<String, Object> explain = query.explain(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees()),
                Company.class
        );
        assertEquals(Map.of("employees", "bound"), explain.get("joinSourceBindings"));
        assertEquals(
                Map.of(
                        "company id", "companies.id",
                        "employee company id", "employees.companyId",
                        "employee title", "employees.title"
                ),
                explain.get("resolvedNaturalFields")
        );
        assertEquals(
                "select * from companies join employees on companies.id = employees.companyId where employees.title = 'Engineer'",
                explain.get("resolvedEquivalentSqlLike")
        );
    }

    @Test
    public void runtimeNaturalVocabularyShouldResolveQualifiedJoinAliases() {
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("companyId", "employer id")
                .field("title", "job title")
                .build());

        NaturalQuery query = runtime.natural().parse(
                "from companies as company join employees as employee "
                        + "on company id equals employee employer id "
                        + "show company where employee job title is Engineer"
        );

        List<Company> rows = query.filter(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees()),
                Company.class
        );
        assertEquals(List.of("Acme"), rows.stream().map(row -> row.name).toList());

        Map<String, Object> explain = query.explain(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees()),
                Company.class
        );
        assertEquals(
                Map.of(
                        "company id", "companies.id",
                        "employee employer id", "employees.companyId",
                        "employee job title", "employees.title"
                ),
                explain.get("resolvedNaturalFields")
        );
    }

    @Test
    public void naturalJoinQueryShouldSupportDatasetBundleExecution() {
        DatasetBundle bundle = DatasetBundle.of(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );

        List<Company> rows = PojoLensNatural.parse(
                        "from companies as company join employees as employee "
                                + "on company id equals employee company id "
                                + "show company where employee title is Engineer"
                )
                .bindTyped(bundle, Company.class)
                .filter();

        assertEquals(List.of("Acme"), rows.stream()
                .map(row -> row.name)
                .toList());
    }

    public static class DepartmentPayrollRow {
        public String dept;
        public long totalPayroll;
        public long headcount;

        public DepartmentPayrollRow() {
        }
    }

    public static class PeriodPayrollRow {
        public String period;
        public long payroll;

        public PeriodPayrollRow() {
        }
    }

    public static class ComputedNaturalSalaryRow {
        public String name;
        public double adjustedSalary;

        public ComputedNaturalSalaryRow() {
        }
    }

    public static class NaturalWindowAliasRow {
        public String dept;
        public String name;
        public int pay;
        public long topRank;

        public NaturalWindowAliasRow() {
        }
    }

    private static List<String> normalizeDepartmentPeriodAgg(List<DepartmentPeriodAgg> rows) {
        return rows.stream()
                .map(row -> row.department + "|" + row.period + "|" + row.total + "|" + row.payroll)
                .sorted()
                .collect(Collectors.toList());
    }
}
