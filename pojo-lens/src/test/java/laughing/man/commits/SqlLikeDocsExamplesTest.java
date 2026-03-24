package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartQueryPreset;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.SqlLikeLintCodes;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlLikeTemplate;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import laughing.man.commits.testutil.SqlLikeDocsProjections.DepartmentDenseRank;
import laughing.man.commits.testutil.SqlLikeDocsProjections.DepartmentHeadcount;
import laughing.man.commits.testutil.SqlLikeDocsProjections.DepartmentHeadcountByAlias;
import laughing.man.commits.testutil.SqlLikeDocsProjections.DepartmentRunningTotal;
import laughing.man.commits.testutil.SqlLikeDocsProjections.DepartmentSalaryRank;
import laughing.man.commits.testutil.SqlLikeDocsProjections.PeriodHeadcount;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;

public class SqlLikeDocsExamplesTest {

    @Test
    public void readmeFluentQuickStartExampleShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true)
        );

        List<Employee> results = PojoLens.newQueryBuilder(source)
                .addRule("department", "Engineering", Clauses.EQUAL)
                .addOrder("salary", 1)
                .limit(10)
                .initFilter()
                .filter(Sort.ASC, Employee.class);

        assertEquals(2, results.size());
        assertEquals(120000, results.get(0).salary);
        assertEquals(130000, results.get(1).salary);
    }

    @Test
    public void readmeSqlLikeQuickStartExampleShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false)
        );

        List<Employee> rows = PojoLens
                .parse("select name, salary where department = 'Engineering' and active = true order by salary desc limit 10")
                .filter(source, Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Cara", rows.get(0).name);
        assertEquals(130000, rows.get(0).salary);
        assertEquals("Alice", rows.get(1).name);
        assertEquals(120000, rows.get(1).salary);
    }

    @Test
    public void docsRecipeOffsetPaginationShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, true)
        );

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc limit 2 offset 1")
                .filter(source, Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).name);
        assertEquals("Dan", rows.get(1).name);
    }

    @Test
    public void docsRecipeParameterizedOffsetPaginationShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, true)
        );

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc limit :limit offset :offset")
                .params(Map.of("limit", 2, "offset", 1))
                .filter(source, Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).name);
        assertEquals("Dan", rows.get(1).name);
    }

    @Test
    public void docsRecipeKeysetPaginationPatternShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, true)
        );

        List<Employee> rows = PojoLens
                .parse("where active = true and ((salary < :lastSalary) or (salary = :lastSalary and id < :lastId)) "
                        + "order by salary desc, id desc limit 20")
                .params(Map.of("lastSalary", 120000, "lastId", 1))
                .filter(source, Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Dan", rows.get(0).name);
        assertEquals("Bob", rows.get(1).name);
    }

    @Test
    public void docsRecipeFirstClassKeysetCursorShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, true)
        );

        SqlLikeCursor cursor = PojoLens.newKeysetCursorBuilder()
                .put("salary", 120000)
                .put("id", 1)
                .build();

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc, id desc limit 20")
                .keysetAfter(cursor)
                .filter(source, Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Dan", rows.get(0).name);
        assertEquals("Bob", rows.get(1).name);

        String token = cursor.toToken();
        SqlLikeCursor decoded = PojoLens.parseKeysetCursor(token);
        assertEquals(cursor, decoded);
    }

    @Test
    public void docsRecipeWindowRankingShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Engineering", 120000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Finance", 100000, now, true),
                new Employee(5, "Erin", "Finance", 110000, now, true)
        );

        List<DepartmentSalaryRank> rows = PojoLens
                .parse("select department as dept, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true order by dept asc, rn asc")
                .filter(source, DepartmentSalaryRank.class);

        assertEquals(5, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(1L, rows.get(0).rn);
        assertEquals("Engineering", rows.get(1).dept);
        assertEquals("Alice", rows.get(1).name);
        assertEquals(2L, rows.get(1).rn);
        assertEquals("Finance", rows.get(3).dept);
        assertEquals(1L, rows.get(3).rn);
    }

    @Test
    public void docsRecipeQualifyTopPerDepartmentShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Engineering", 120000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Finance", 100000, now, true),
                new Employee(5, "Erin", "Finance", 110000, now, true)
        );

        List<DepartmentSalaryRank> rows = PojoLens
                .parse("select department as dept, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true qualify rn <= 1 order by dept asc")
                .filter(source, DepartmentSalaryRank.class);

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Cara", rows.get(0).name);
        assertEquals("Finance", rows.get(1).dept);
        assertEquals("Erin", rows.get(1).name);
    }

    @Test
    public void docsRecipeDenseRankPerGroupShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Engineering", 120000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Finance", 100000, now, true),
                new Employee(5, "Erin", "Finance", 110000, now, true)
        );

        List<DepartmentDenseRank> rows = PojoLens
                .parse("select department as dept, name, salary, "
                        + "dense_rank() over (partition by department order by salary desc) as dr "
                        + "where active = true order by dept asc, dr asc, name asc")
                .filter(source, DepartmentDenseRank.class);

        assertEquals(5, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(1L, rows.get(0).dr);
        assertEquals("Engineering", rows.get(1).dept);
        assertEquals(2L, rows.get(1).dr);
        assertEquals("Finance", rows.get(3).dept);
        assertEquals("Erin", rows.get(3).name);
        assertEquals(1L, rows.get(3).dr);
    }

    @Test
    public void docsRecipeRunningTotalShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Engineering", 120000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Finance", 100000, now, true),
                new Employee(5, "Erin", "Finance", 110000, now, true)
        );

        List<DepartmentRunningTotal> rows = PojoLens
                .parse("select department as dept, name, salary, "
                        + "sum(salary) over (partition by department order by salary desc "
                        + "rows between unbounded preceding and current row) as runningTotal "
                        + "where active = true order by dept asc, runningTotal asc")
                .filter(source, DepartmentRunningTotal.class);

        assertEquals(5, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(130000L, rows.get(0).runningTotal);
        assertEquals("Engineering", rows.get(1).dept);
        assertEquals("Alice", rows.get(1).name);
        assertEquals(250000L, rows.get(1).runningTotal);
        assertEquals("Engineering", rows.get(2).dept);
        assertEquals("Bob", rows.get(2).name);
        assertEquals(370000L, rows.get(2).runningTotal);
        assertEquals("Finance", rows.get(3).dept);
        assertEquals("Erin", rows.get(3).name);
        assertEquals(110000L, rows.get(3).runningTotal);
        assertEquals("Finance", rows.get(4).dept);
        assertEquals("Dan", rows.get(4).name);
        assertEquals(210000L, rows.get(4).runningTotal);
    }

    @Test
    public void readmeSqlLikeParameterizedExampleShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false)
        );

        List<Employee> rows = PojoLens
                .parse("where department = :dept and salary >= :minSalary and active = :active order by salary desc")
                .params(Map.of("dept", "Engineering", "minSalary", 120000, "active", true))
                .filter(source, Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Cara", rows.get(0).name);
        assertEquals("Alice", rows.get(1).name);
    }

    @Test
    public void docsRecipeTypedSqlParamsShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false)
        );

        List<Employee> rows = PojoLens
                .parse("where department = :dept and salary >= :minSalary and active = :active order by salary desc")
                .params(SqlParams.builder()
                        .put("dept", "Engineering")
                        .put("minSalary", 120000)
                        .put("active", true)
                        .build())
                .filter(source, Employee.class);

        assertEquals(2, rows.size());
        assertEquals("Cara", rows.get(0).name);
        assertEquals("Alice", rows.get(1).name);
    }

    @Test
    public void docsRecipeLintModeShouldWork() {
        SqlLikeQuery query = PojoLens
                .parse("select * from companies where title = 'Engineer' limit 5")
                .lintMode();

        List<SqlLikeLintWarning> warnings = query.lintWarnings();
        assertEquals(3, warnings.size());
        assertEquals(SqlLikeLintCodes.SELECT_WILDCARD, warnings.get(0).code());

        Map<String, Object> explain = query.explain();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> explainWarnings = (List<Map<String, Object>>) explain.get("lintWarnings");
        assertEquals(3, explainWarnings.size());

        SqlLikeQuery suppressed = PojoLens
                .parse("select * from companies limit 5")
                .lintMode()
                .suppressLintWarnings(SqlLikeLintCodes.SELECT_WILDCARD);
        assertEquals(1, suppressed.lintWarnings().size());
    }

    @Test
    public void docsRecipeRuntimePolicyPresetsShouldWork() {
        PojoLensRuntime devRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);
        PojoLensRuntime prodRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.PROD);
        PojoLensRuntime testRuntime = PojoLens.newRuntime(PojoLensRuntimePreset.TEST);

        assertTrue(devRuntime.isStrictParameterTypes());
        assertTrue(devRuntime.isLintMode());
        assertFalse(prodRuntime.isStrictParameterTypes());
        assertFalse(prodRuntime.isLintMode());
        assertFalse(testRuntime.sqlLikeCache().isEnabled());

        prodRuntime.setLintMode(true);
        prodRuntime.setStrictParameterTypes(true);
        assertTrue(prodRuntime.isLintMode());
        assertTrue(prodRuntime.isStrictParameterTypes());
    }

    @Test
    public void docsRecipeWhereInSubqueryShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, false),
                new Employee(3, "Cara", "Engineering", 130000, now, false),
                new Employee(4, "Dan", "Support", 70000, now, false)
        );

        List<Employee> selfSourceRows = PojoLens
                .parse("where department in (select department where active = true)")
                .filter(source, Employee.class);
        assertEquals(Arrays.asList("Alice", "Cara"),
                selfSourceRows.stream().map(r -> r.name).collect(Collectors.toList()));

        List<Company> companies = sampleCompanies();
        List<Company> namedSourceRows = PojoLens
                .parse("where id in (select companyId from employees where title = 'Engineer')")
                .filter(companies, Map.of("employees", sampleCompanyEmployees()), Company.class);
        assertEquals(1, namedSourceRows.size());
        assertEquals("Acme", namedSourceRows.get(0).name);
    }

    @Test
    public void docsRecipeTemplateWithParameterSchemaShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false)
        );

        SqlLikeTemplate template = PojoLens.template(
                "where department = :dept and salary >= :minSalary and active = :active order by salary desc",
                "dept",
                "minSalary",
                "active"
        );

        List<Employee> engineeringRows = template
                .bind(Map.of("dept", "Engineering", "minSalary", 120000, "active", true))
                .filter(source, Employee.class);
        List<Employee> financeRows = template
                .bind(SqlParams.builder()
                        .put("dept", "Finance")
                        .put("minSalary", 80000)
                        .put("active", true)
                        .build())
                .filter(source, Employee.class);

        assertEquals(Arrays.asList("Cara", "Alice"), engineeringRows.stream().map(r -> r.name).toList());
        assertEquals(Arrays.asList("Bob"), financeRows.stream().map(r -> r.name).toList());
    }

    @Test
    public void readmeBindThenExecuteExampleShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true)
        );

        SqlLikeQuery query = PojoLens.parse("where salary >= 90000 order by salary asc");

        List<Employee> rows = query.bindTyped(source, Employee.class).filter();

        assertEquals(3, rows.size());
        List<Integer> salaries = rows.stream().map(r -> r.salary).toList();
        assertEquals(Arrays.asList(90000, 120000, 130000).size(), salaries.size());
        assertTrue(salaries.contains(90000));
        assertTrue(salaries.contains(120000));
        assertTrue(salaries.contains(130000));
    }

    @Test
    public void docsRecipeTypedBindWithParametersShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true)
        );

        SqlLikeQuery query = PojoLens.parse("where salary >= :minSalary order by salary asc")
                .params(Map.of("minSalary", 90000));

        List<Employee> rows = query.bindTyped(source, Employee.class).filter();
        assertEquals(3, rows.size());
        assertEquals(90000, rows.get(0).salary);
        assertEquals(120000, rows.get(1).salary);
        assertEquals(130000, rows.get(2).salary);
    }

    @Test
    public void docsRecipeStreamingExecutionOutputShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false)
        );

        List<String> names;
        try (Stream<Employee> rows = PojoLens
                .parse("where active = true limit 2")
                .stream(source, Employee.class)) {
            names = rows.map(r -> r.name).toList();
        }

        assertEquals(Arrays.asList("Alice", "Bob"), names);
    }

    @Test
    public void readmeAliasProjectionExampleShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true)
        );

        List<EmployeeSummary> rows = PojoLens
                .parse("select name as employeeName, salary as annualSalary where salary >= 100000 order by salary asc")
                .filter(source, EmployeeSummary.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).employeeName);
        assertEquals(120000, rows.get(0).annualSalary);
        assertEquals("Cara", rows.get(1).employeeName);
        assertEquals(130000, rows.get(1).annualSalary);
    }

    @Test
    public void readmeJoinWithRuntimeBindingExampleShouldWork() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();

        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("employees", employees);

        List<Company> rows = PojoLens
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .filter(companies, joinSources, Company.class);

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
        assertEquals("Acme", rows.get(0).name);
    }

    @Test
    public void readmeJoinWithTypedBindingsExampleShouldWork() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        JoinBindings joinBindings = JoinBindings.of("employees", employees);

        List<Company> rows = PojoLens
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .filter(companies, joinBindings, Company.class);

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
        assertEquals("Acme", rows.get(0).name);
    }

    @Test
    public void docsRecipeJoinWithBuilderBindingsShouldWork() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> employees = sampleCompanyEmployees();
        JoinBindings joinBindings = JoinBindings.builder()
                .add("employees", employees)
                .build();

        List<Company> rows = PojoLens
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .filter(companies, joinBindings, Company.class);

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
    }

    @Test
    public void readmeHavingExampleShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false),
                new Employee(5, "Erin", "Finance", 95000, now, true)
        );

        List<DepartmentHeadcount> rows = PojoLens
                .parse("select department, count(*) as headcount group by department having headcount >= 2 order by headcount desc")
                .filter(source, DepartmentHeadcount.class);

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(3L, rows.get(0).headcount);
        assertEquals("Finance", rows.get(1).department);
        assertEquals(2L, rows.get(1).headcount);
    }

    @Test
    public void docsGroupedAliasRecipeShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false),
                new Employee(5, "Erin", "Finance", 95000, now, true)
        );

        List<DepartmentHeadcountByAlias> rows = PojoLens
                .parse("select department as dept, count(*) as headcount "
                        + "group by dept having dept = 'Engineering' "
                        + "order by sum(salary) desc")
                .filter(source, DepartmentHeadcountByAlias.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals(3L, rows.get(0).headcount);
    }

    @Test
    public void docsChartPresetRecipeShouldWork() {
        ChartQueryPreset<DepartmentHeadcount> preset = ChartQueryPresets
                .categoryCounts("department", "headcount", DepartmentHeadcount.class);

        List<DepartmentHeadcount> rows = preset.rows(sampleEmployees());
        ChartData chart = preset.chart(sampleEmployees());

        assertEquals(2, rows.size());
        assertEquals(ChartType.BAR, preset.chartSpec().type());
        assertEquals(2, chart.getLabels().size());
    }

    @Test
    public void docsTimeSeriesChartPresetRecipeShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true)
        );

        ChartQueryPreset<PeriodHeadcount> preset = ChartQueryPresets
                .timeSeriesCounts("hireDate", TimeBucket.MONTH, "period", "headcount", PeriodHeadcount.class);

        List<PeriodHeadcount> rows = preset.rows(source);
        assertEquals(1, rows.size());
        assertEquals(ChartType.LINE, preset.chartSpec().type());
    }

    @Test
    public void readmeSqlLikeChartExampleShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false),
                new Employee(5, "Erin", "Finance", 95000, now, true)
        );

        ChartData chart = PojoLens
                .parse("select department, count(*) as headcount group by department order by headcount desc")
                .chart(source, DepartmentHeadcount.class, ChartSpec.of(ChartType.BAR, "department", "headcount"));

        assertEquals(2, chart.getLabels().size());
        assertEquals("Engineering", chart.getLabels().get(0));
        assertEquals("Finance", chart.getLabels().get(1));
        assertEquals(1, chart.getDatasets().size());
        assertEquals(3d, chart.getDatasets().get(0).getValues().get(0), 0.0001d);
        assertEquals(2d, chart.getDatasets().get(0).getValues().get(1), 0.0001d);
    }

    @Test
    public void docsRecipeExecutionExplainWithStageCountsShouldWork() {
        Date now = new Date();
        List<Employee> source = Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false)
        );

        Map<String, Object> explain = PojoLens
                .parse("where active = true order by salary desc limit 2")
                .explain(source, Employee.class);

        assertEquals("where active = true order by salary desc limit 2", explain.get("normalizedQuery"));
        assertEquals("DESC", explain.get("resolvedSortDirection"));
        assertEquals("direct", explain.get("projectionMode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> stageCounts = (Map<String, Object>) explain.get("stageRowCounts");
        @SuppressWarnings("unchecked")
        Map<String, Object> whereStage = (Map<String, Object>) stageCounts.get("where");
        assertEquals(true, whereStage.get("applied"));
        assertEquals(4, ((Number) whereStage.get("before")).intValue());
        assertEquals(3, ((Number) whereStage.get("after")).intValue());
    }
}

