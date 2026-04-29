package laughing.man.commits.dsl;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.PojoLensSql;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.internal.builder.QueryWindowFrame;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExecutionGuardException;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.CommonStatsProjections.DepartmentCount;
import laughing.man.commits.testutil.WindowTestFixtures.DepartmentAgg;
import laughing.man.commits.testutil.WindowTestFixtures.DepartmentRank;
import laughing.man.commits.testutil.WindowTestFixtures.WindowEmployee;
import laughing.man.commits.testutil.WindowTestFixtures.WindowMetricInput;
import laughing.man.commits.testutil.WindowTestFixtures.WindowMetricProjection;
import laughing.man.commits.testutil.WindowTestFixtures.WindowRankProjection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.WindowTestFixtures.sampleWindowMetricInputs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypedQueryContractTest {

    private static final TypedField<Employee, String> NAME = TypedField.of("name", String.class);
    private static final TypedField<Employee, Integer> SALARY = TypedField.of("salary", Integer.class);
    private static final TypedField<Employee, Boolean> ACTIVE = TypedField.of("active", Boolean.class);
    private static final TypedField<Employee, String> DEPT = TypedField.of("department", String.class);
    private static final TypedField<Company, Integer> COMPANY_ID = TypedField.of("id", Integer.class);
    private static final TypedField<Company, String> JOINED_TITLE = TypedField.of("title", String.class);
    private static final TypedField<CompanyEmployee, Integer> EMPLOYEE_COMPANY_ID =
            TypedField.of("companyId", Integer.class);
    private static final TypedField<DepartmentCount, Long> TOTAL = TypedField.of("total", Long.class);
    private static final TypedField<DepartmentAgg, Long> EMPLOYEE_COUNT = TypedField.of("employeeCount", Long.class);
    private static final TypedField<DepartmentAgg, Long> TOTAL_SALARY = TypedField.of("totalSalary", Long.class);
    private static final TypedField<JoinedTitleCount, Long> JOINED_TOTAL = TypedField.of("total", Long.class);
    private static final TypedField<TotalsRow, Long> PAYROLL = TypedField.of("payroll", Long.class);
    private static final TypedField<DepartmentRank, Long> RN = TypedField.of("rn", Long.class);
    private static final TypedField<WindowRankProjection, Long> RK = TypedField.of("rk", Long.class);
    private static final TypedField<WindowRankProjection, Long> DR = TypedField.of("dr", Long.class);
    private static final TypedField<WindowMetricInput, String> WINDOW_DEPT = TypedField.of("department", String.class);
    private static final TypedField<WindowMetricInput, Integer> WINDOW_SEQ = TypedField.of("seq", Integer.class);
    private static final TypedField<WindowMetricInput, Integer> WINDOW_AMOUNT = TypedField.of("amount", Integer.class);
    private static final TypedField<WindowMetricProjection, Long> RUNNING_SUM =
            TypedField.of("runningSum", Long.class);
    private static final TypedField<WindowMetricProjection, Long> RUNNING_COUNT_ALL =
            TypedField.of("runningCountAll", Long.class);

    // fixtures: Alice(Eng,120k,active), Bob(Fin,90k,active), Cara(Eng,130k,active), Dan(Eng,110k,inactive)

    // --- Public API surface contract ---

    @Test
    void stableTypedQueryContractShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(TypedQuery.class, "from", Class.class);
        requirePublicMethod(TypedQuery.class, "select", TypedField[].class);
        requirePublicMethod(TypedQuery.class, "where", TypedPredicate.class);
        requirePublicMethod(TypedQuery.class, "join", String.class, TypedField.class, TypedField.class, Join.class);
        requirePublicMethod(TypedQuery.class, "groupBy", TypedField.class);
        requirePublicMethod(TypedQuery.class, "count", String.class);
        requirePublicMethod(TypedQuery.class, "count", TypedField.class);
        requirePublicMethod(TypedQuery.class, "metric", TypedField.class, Metric.class, String.class);
        requirePublicMethod(TypedQuery.class, "metric", TypedField.class, Metric.class, TypedField.class);
        requirePublicMethod(TypedQuery.class, "having", TypedPredicate.class);
        requirePublicMethod(TypedQuery.class, "window",
                WindowFunction.class, String.class, List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "window",
                WindowFunction.class, TypedField.class, List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "window",
                WindowFunction.class, TypedField.class, String.class, List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "window",
                WindowFunction.class, TypedField.class, String.class, QueryWindowFrame.class,
                List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "window",
                WindowFunction.class, TypedField.class, TypedField.class, List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "window",
                WindowFunction.class, TypedField.class, TypedField.class, QueryWindowFrame.class,
                List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "windowCountAll",
                String.class, List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "windowCountAll",
                String.class, QueryWindowFrame.class, List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "windowCountAll",
                TypedField.class, List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "windowCountAll",
                TypedField.class, QueryWindowFrame.class, List.class, TypedField[].class);
        requirePublicMethod(TypedQuery.class, "qualify", TypedPredicate.class);
        requirePublicMethod(TypedQuery.class, "orderBy", TypedField.class);
        requirePublicMethod(TypedQuery.class, "orderByDesc", TypedField.class);
        requirePublicMethod(TypedQuery.class, "limit", int.class);
        requirePublicMethod(TypedQuery.class, "offset", int.class);
        requirePublicMethod(TypedQuery.class, "filter", List.class);
        requirePublicMethod(TypedQuery.class, "filter", List.class, Class.class);
        requirePublicMethod(TypedQuery.class, "filter", List.class, JoinBindings.class);
        requirePublicMethod(TypedQuery.class, "filter", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(TypedQuery.class, "filter", DatasetBundle.class);
        requirePublicMethod(TypedQuery.class, "filter", DatasetBundle.class, Class.class);
        requirePublicMethod(TypedQuery.class, "executionGuard", QueryExecutionGuard.class);
        requirePublicMethod(TypedQuery.class, "explain", List.class);
        requirePublicMethod(TypedQuery.class, "explain", List.class, JoinBindings.class);
        requirePublicMethod(TypedQuery.class, "explain", DatasetBundle.class);
        requirePublicMethod(TypedQuery.class, "schema", List.class);
        requirePublicMethod(TypedQuery.class, "schema", List.class, Class.class);
        requirePublicMethod(TypedQuery.class, "schema", List.class, JoinBindings.class);
        requirePublicMethod(TypedQuery.class, "schema", List.class, JoinBindings.class, Class.class);
        requirePublicMethod(TypedQuery.class, "schema", DatasetBundle.class);
        requirePublicMethod(TypedQuery.class, "schema", DatasetBundle.class, Class.class);
        requirePublicMethod(TypedQuery.class, "entityClass");
        requirePublicMethod(TypedQuery.class, "selectFields");
        requirePublicMethod(TypedQuery.class, "wherePredicate");
        requirePublicMethod(TypedQuery.class, "havingPredicate");
        requirePublicMethod(TypedQuery.class, "qualifyPredicate");
        requirePublicMethod(TypedQuery.class, "hasWhere");
        requirePublicMethod(TypedQuery.class, "hasHaving");
        requirePublicMethod(TypedQuery.class, "hasWindows");
        requirePublicMethod(TypedQuery.class, "hasQualify");
        requirePublicMethod(TypedQuery.class, "hasSelect");
        requirePublicMethod(TypedQuery.class, "hasOrderBy");
        requirePublicMethod(TypedQuery.class, "hasJoins");
        requirePublicMethod(TypedQuery.class, "hasGroupBy");
        requirePublicMethod(TypedQuery.class, "hasMetrics");
        requirePublicMethod(TypedQuery.class, "hasLimit");
        requirePublicMethod(TypedQuery.class, "hasOffset");
        requirePublicStaticMethod(TypedWindowOrder.class, "asc", TypedField.class);
        requirePublicStaticMethod(TypedWindowOrder.class, "desc", TypedField.class);
        requirePublicMethod(TypedWindowOrder.class, "fieldName");
        requirePublicMethod(TypedWindowOrder.class, "sort");
        requirePublicStaticMethod(QueryWindowFrame.class, "running");
        requirePublicStaticMethod(QueryWindowFrame.class, "unboundedPrecedingToCurrentRow");
        requirePublicStaticMethod(QueryWindowFrame.class, "rowsPrecedingToCurrentRow", int.class);
        requirePublicStaticMethod(QueryWindowFrame.class, "fullPartition");
        requirePublicMethod(QueryWindowFrame.class, "isRunning");
        requirePublicMethod(QueryWindowFrame.class, "boundedPreceding");
        requirePublicMethod(QueryWindowFrame.class, "precedingRows");
        requirePublicMethod(QueryWindowFrame.class, "isFullPartition");
        requirePublicMethod(QueryWindowFrame.class, "sqlExpression");
        requirePublicMethod(QueryWindowFrame.class, "explainToken");
    }

    // --- Execution behavior ---

    @Test
    void filterWithNoConfigurationReturnsAllRows() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .filter(sampleEmployees());
        assertEquals(4, result.size());
    }

    @Test
    void whereEqFiltersToMatchingRow() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.eq("Alice"))
                .filter(sampleEmployees());
        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).name);
    }

    @Test
    void whereGtFiltersRowsAboveThreshold() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(SALARY.gt(100_000))
                .filter(sampleEmployees());
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(e -> e.salary > 100_000));
    }

    @Test
    void whereLteFiltersRowsAtOrBelowThreshold() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(SALARY.lte(110_000))
                .filter(sampleEmployees());
        assertEquals(2, result.size()); // Bob(90k), Dan(110k)
        assertTrue(result.stream().allMatch(e -> e.salary <= 110_000));
    }

    @Test
    void whereNeExcludesMatchingRow() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.ne("Dan"))
                .filter(sampleEmployees());
        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(e -> e.name.equals("Dan")));
    }

    @Test
    void whereInFiltersToNamedRows() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.in("Alice", "Cara"))
                .filter(sampleEmployees());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.name.equals("Alice")));
        assertTrue(result.stream().anyMatch(e -> e.name.equals("Cara")));
    }

    @Test
    void whereAndCombinesPredicates() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(DEPT.eq("Engineering").and(ACTIVE.eq(true)))
                .filter(sampleEmployees());
        assertEquals(2, result.size()); // Alice and Cara (Dan is inactive)
        assertTrue(result.stream().allMatch(e -> e.department.equals("Engineering") && e.active));
    }

    @Test
    void whereOrIncludesEitherPredicate() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.eq("Alice").or(NAME.eq("Bob")))
                .filter(sampleEmployees());
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> e.name.equals("Alice")));
        assertTrue(result.stream().anyMatch(e -> e.name.equals("Bob")));
    }

    @Test
    void nestedAndOrPreservesPredicateTreeSemantics() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(DEPT.eq("Engineering").and(NAME.eq("Alice").or(NAME.eq("Bob"))))
                .filter(sampleEmployees());

        assertEquals(List.of("Alice"), result.stream().map(e -> e.name).toList());
    }

    @Test
    void nestedOrAndPreservesPredicateTreeSemantics() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.eq("Bob").or(DEPT.eq("Engineering").and(ACTIVE.eq(false))))
                .orderBy(NAME)
                .filter(sampleEmployees());

        assertEquals(List.of("Bob", "Dan"), result.stream().map(e -> e.name).toList());
    }

    @Test
    void limitCapsResultSize() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .limit(2)
                .filter(sampleEmployees());
        assertEquals(2, result.size());
    }

    @Test
    void orderByDescSortsBySalaryDescending() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .orderByDesc(SALARY)
                .filter(sampleEmployees());
        assertEquals(4, result.size());
        assertEquals("Cara", result.get(0).name);
        assertTrue(result.get(0).salary >= result.get(1).salary);
    }

    @Test
    void whereOrderByDescLimitReturnTopActiveEmployees() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true))
                .orderByDesc(SALARY)
                .limit(2)
                .filter(sampleEmployees());
        assertEquals(2, result.size());
        assertEquals("Cara", result.get(0).name);
        assertEquals("Alice", result.get(1).name);
    }

    @Test
    void emptyRowsReturnEmptyList() {
        List<Employee> result = TypedQuery.from(Employee.class)
                .where(NAME.eq("Alice"))
                .filter(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void queryBuilderIsImmutable() {
        TypedQuery<Employee> base = TypedQuery.from(Employee.class);
        TypedQuery<Employee> withWhere = base.where(NAME.eq("Alice"));
        TypedQuery<Employee> withLimit = base.limit(1);

        assertFalse(base.hasWhere());
        assertTrue(withWhere.hasWhere());
        assertFalse(withLimit.hasWhere());
        assertFalse(base.hasLimit());
        assertTrue(withLimit.hasLimit());
        assertFalse(withWhere.hasLimit());
    }

    @Test
    void joinBuilderIsImmutable() {
        TypedQuery<Company> base = TypedQuery.from(Company.class);
        TypedQuery<Company> joined = base.join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN);

        assertFalse(base.hasJoins());
        assertTrue(joined.hasJoins());
    }

    @Test
    void groupedBuilderIsImmutable() {
        TypedQuery<Employee> base = TypedQuery.from(Employee.class);
        TypedQuery<Employee> grouped = base.groupBy(DEPT).count(TOTAL);
        TypedQuery<Employee> withHaving = grouped.having(TOTAL.gte(2L));

        assertFalse(base.hasGroupBy());
        assertFalse(base.hasMetrics());
        assertFalse(grouped.hasHaving());
        assertTrue(grouped.hasGroupBy());
        assertTrue(grouped.hasMetrics());
        assertTrue(withHaving.hasHaving());
    }

    @Test
    void windowBuilderIsImmutable() {
        TypedQuery<Employee> base = TypedQuery.from(Employee.class);
        TypedQuery<Employee> windowed = base.window(
                WindowFunction.ROW_NUMBER,
                RN,
                List.of(TypedWindowOrder.desc(SALARY)),
                DEPT
        );
        TypedQuery<Employee> qualified = windowed.qualify(RN.lte(1L));

        assertFalse(base.hasWindows());
        assertFalse(base.hasQualify());
        assertTrue(windowed.hasWindows());
        assertFalse(windowed.hasQualify());
        assertTrue(qualified.hasQualify());
    }

    @Test
    void accessorsReflectConfiguredState() {
        TypedQuery<Employee> q = TypedQuery.from(Employee.class)
                .where(SALARY.gt(100_000))
                .orderByDesc(SALARY)
                .limit(5)
                .offset(1);

        assertEquals(Employee.class, q.entityClass());
        assertTrue(q.hasWhere());
        assertFalse(q.hasHaving());
        assertFalse(q.hasWindows());
        assertFalse(q.hasQualify());
        assertTrue(q.hasOrderBy());
        assertTrue(q.hasLimit());
        assertTrue(q.hasOffset());
        assertEquals(5, q.limit());
        assertEquals(1, q.offset());
        assertFalse(q.hasSelect());
    }

    @Test
    void typedJoinShouldSupportJoinBindingsExecution() {
        List<Company> result = TypedQuery.from(Company.class)
                .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
                .where(JOINED_TITLE.eq("Engineer"))
                .filter(sampleCompanies(), JoinBindings.of("employees", sampleCompanyEmployees()));

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).id);
    }

    @Test
    void typedJoinShouldSupportDatasetBundleExecution() {
        DatasetBundle bundle = DatasetBundle.of(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );

        List<Company> result = TypedQuery.from(Company.class)
                .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
                .where(JOINED_TITLE.eq("Engineer"))
                .filter(bundle);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).id);
    }

    @Test
    void typedJoinShouldMatchEquivalentSqlLikeExecution() {
        JoinBindings joinBindings = JoinBindings.of("employees", sampleCompanyEmployees());

        List<Integer> typedIds = TypedQuery.from(Company.class)
                .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
                .where(JOINED_TITLE.eq("Engineer"))
                .filter(sampleCompanies(), joinBindings)
                .stream()
                .map(company -> company.id)
                .toList();

        List<Integer> sqlLikeIds = PojoLensSql
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .filter(sampleCompanies(), joinBindings, Company.class)
                .stream()
                .map(company -> company.id)
                .toList();

        assertEquals(sqlLikeIds, typedIds);
    }

    @Test
    void typedGroupedCountShouldReturnDepartmentTotals() {
        List<DepartmentCount> result = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true))
                .groupBy(DEPT)
                .count(TOTAL)
                .orderByDesc(TOTAL)
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(2, result.size());
        assertEquals("Engineering", result.get(0).department);
        assertEquals(2L, result.get(0).total);
        assertEquals("Finance", result.get(1).department);
        assertEquals(1L, result.get(1).total);
    }

    @Test
    void typedGroupedAggregationShouldMatchEquivalentSqlLikeExecution() {
        List<String> typedRows = TypedQuery.from(Employee.class)
                .groupBy(DEPT)
                .count(EMPLOYEE_COUNT)
                .metric(SALARY, Metric.SUM, TOTAL_SALARY)
                .orderByDesc(TOTAL_SALARY)
                .filter(sampleEmployees(), DepartmentAgg.class)
                .stream()
                .map(row -> row.department + ":" + row.employeeCount + ":" + row.totalSalary)
                .toList();

        List<String> sqlLikeRows = PojoLensSql
                .parse("select department, count(*) as employeeCount, sum(salary) as totalSalary "
                        + "group by department order by totalSalary desc")
                .filter(sampleEmployees(), DepartmentAgg.class)
                .stream()
                .map(row -> row.department + ":" + row.employeeCount + ":" + row.totalSalary)
                .toList();

        assertEquals(sqlLikeRows, typedRows);
    }

    @Test
    void typedHavingShouldFilterGroupedRows() {
        List<DepartmentCount> result = TypedQuery.from(Employee.class)
                .groupBy(DEPT)
                .count(TOTAL)
                .having(TOTAL.gte(2L))
                .orderByDesc(TOTAL)
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(1, result.size());
        assertEquals("Engineering", result.get(0).department);
        assertEquals(3L, result.get(0).total);
    }

    @Test
    void typedHavingShouldMatchEquivalentSqlLikeExecution() {
        List<String> typedRows = TypedQuery.from(Employee.class)
                .groupBy(DEPT)
                .count(EMPLOYEE_COUNT)
                .metric(SALARY, Metric.SUM, TOTAL_SALARY)
                .having(TOTAL_SALARY.gte(220_000L))
                .orderByDesc(TOTAL_SALARY)
                .filter(sampleEmployees(), DepartmentAgg.class)
                .stream()
                .map(row -> row.department + ":" + row.employeeCount + ":" + row.totalSalary)
                .toList();

        List<String> sqlLikeRows = PojoLensSql
                .parse("select department, count(*) as employeeCount, sum(salary) as totalSalary "
                        + "group by department having totalSalary >= 220000 order by totalSalary desc")
                .filter(sampleEmployees(), DepartmentAgg.class)
                .stream()
                .map(row -> row.department + ":" + row.employeeCount + ":" + row.totalSalary)
                .toList();

        assertEquals(sqlLikeRows, typedRows);
    }

    @Test
    void typedWindowQualifyShouldReturnTopPerDepartment() {
        List<DepartmentRank> result = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true))
                .window(WindowFunction.ROW_NUMBER, RN, List.of(TypedWindowOrder.desc(SALARY)), DEPT)
                .qualify(RN.lte(1L))
                .orderBy(DEPT)
                .orderBy(RN)
                .filter(sampleEmployees(), DepartmentRank.class)
                .stream()
                .sorted(Comparator.comparing(row -> row.department))
                .toList();

        assertEquals(2, result.size());
        assertEquals("Engineering", result.get(0).department);
        assertEquals("Cara", result.get(0).name);
        assertEquals(1L, result.get(0).rn);
        assertEquals("Finance", result.get(1).department);
        assertEquals("Bob", result.get(1).name);
        assertEquals(1L, result.get(1).rn);
    }

    @Test
    void typedWindowQualifyShouldMatchEquivalentSqlLikeExecution() {
        List<String> typedRows = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true))
                .window(WindowFunction.ROW_NUMBER, RN, List.of(TypedWindowOrder.desc(SALARY)), DEPT)
                .qualify(RN.lte(1L))
                .orderBy(DEPT)
                .orderBy(RN)
                .filter(sampleEmployees(), DepartmentRank.class)
                .stream()
                .map(row -> row.department + ":" + row.name + ":" + row.rn)
                .sorted()
                .toList();

        List<String> sqlLikeRows = PojoLensSql
                .parse("select department, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true qualify rn <= 1 order by department asc, rn asc")
                .filter(sampleEmployees(), DepartmentRank.class)
                .stream()
                .map(row -> row.department + ":" + row.name + ":" + row.rn)
                .sorted()
                .toList();

        assertEquals(sqlLikeRows, typedRows);
    }

    @Test
    void typedRankWindowsShouldMatchEquivalentSqlLikeExecution() {
        List<WindowEmployee> source = Arrays.asList(
                new WindowEmployee(1, "Alice", "Engineering", 120000, true),
                new WindowEmployee(2, "Bob", "Engineering", 120000, true),
                new WindowEmployee(3, "Cara", "Engineering", 130000, true),
                new WindowEmployee(4, "Dan", "Finance", 110000, true)
        );
        TypedField<WindowEmployee, String> windowName = TypedField.of("name", String.class);
        TypedField<WindowEmployee, Integer> windowSalary = TypedField.of("salary", Integer.class);
        TypedField<WindowEmployee, Boolean> windowActive = TypedField.of("active", Boolean.class);

        List<String> typedRows = TypedQuery.from(WindowEmployee.class)
                .where(windowActive.eq(true))
                .window(WindowFunction.RANK, RK, List.of(TypedWindowOrder.desc(windowSalary)))
                .window(WindowFunction.DENSE_RANK, DR, List.of(TypedWindowOrder.desc(windowSalary)))
                .orderBy(RK)
                .orderBy(windowName)
                .filter(source, WindowRankProjection.class)
                .stream()
                .map(row -> row.name + ":" + row.rk + ":" + row.dr)
                .sorted()
                .toList();

        List<String> sqlLikeRows = PojoLensSql
                .parse("select name, salary, rank() over (order by salary desc) as rk, "
                        + "dense_rank() over (order by salary desc) as dr "
                        + "where active = true order by rk asc, name asc")
                .filter(source, WindowRankProjection.class)
                .stream()
                .map(row -> row.name + ":" + row.rk + ":" + row.dr)
                .sorted()
                .toList();

        assertEquals(sqlLikeRows, typedRows);
    }

    @Test
    void typedRunningAggregateWindowsShouldMatchEquivalentSqlLikeExecution() {
        List<String> typedRows = TypedQuery.from(WindowMetricInput.class)
                .window(WindowFunction.SUM, WINDOW_AMOUNT, RUNNING_SUM,
                        List.of(TypedWindowOrder.asc(WINDOW_SEQ)), WINDOW_DEPT)
                .windowCountAll(RUNNING_COUNT_ALL, List.of(TypedWindowOrder.asc(WINDOW_SEQ)), WINDOW_DEPT)
                .orderBy(WINDOW_DEPT)
                .orderBy(WINDOW_SEQ)
                .filter(sampleWindowMetricInputs(), WindowMetricProjection.class)
                .stream()
                .map(row -> row.department + ":" + row.seq + ":" + row.runningSum + ":" + row.runningCountAll)
                .toList();

        List<String> sqlLikeRows = PojoLensSql
                .parse("select department, seq, amount, "
                        + "sum(amount) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and current row) as runningSum, "
                        + "count(*) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and current row) as runningCountAll "
                        + "order by department asc, seq asc")
                .filter(sampleWindowMetricInputs(), WindowMetricProjection.class)
                .stream()
                .map(row -> row.department + ":" + row.seq + ":" + row.runningSum + ":" + row.runningCountAll)
                .toList();

        assertEquals(sqlLikeRows, typedRows);
    }

    @Test
    void typedBoundedAggregateWindowsShouldMatchEquivalentSqlLikeExecution() {
        QueryWindowFrame onePreceding = QueryWindowFrame.rowsPrecedingToCurrentRow(1);

        List<String> typedRows = TypedQuery.from(WindowMetricInput.class)
                .window(WindowFunction.SUM, WINDOW_AMOUNT, RUNNING_SUM,
                        onePreceding, List.of(TypedWindowOrder.asc(WINDOW_SEQ)), WINDOW_DEPT)
                .windowCountAll(RUNNING_COUNT_ALL,
                        onePreceding, List.of(TypedWindowOrder.asc(WINDOW_SEQ)), WINDOW_DEPT)
                .orderBy(WINDOW_DEPT)
                .orderBy(WINDOW_SEQ)
                .filter(sampleWindowMetricInputs(), WindowMetricProjection.class)
                .stream()
                .map(row -> row.department + ":" + row.seq + ":" + row.runningSum + ":" + row.runningCountAll)
                .toList();

        List<String> sqlLikeRows = PojoLensSql
                .parse("select department, seq, amount, "
                        + "sum(amount) over (partition by department order by seq asc "
                        + "rows between 1 preceding and current row) as runningSum, "
                        + "count(*) over (partition by department order by seq asc "
                        + "rows between 1 preceding and current row) as runningCountAll "
                        + "order by department asc, seq asc")
                .filter(sampleWindowMetricInputs(), WindowMetricProjection.class)
                .stream()
                .map(row -> row.department + ":" + row.seq + ":" + row.runningSum + ":" + row.runningCountAll)
                .toList();

        assertEquals(sqlLikeRows, typedRows);
    }

    @Test
    void typedFullPartitionAggregateWindowsShouldMatchEquivalentSqlLikeExecution() {
        QueryWindowFrame fullPartition = QueryWindowFrame.fullPartition();

        List<String> typedRows = TypedQuery.from(WindowMetricInput.class)
                .window(WindowFunction.SUM, WINDOW_AMOUNT, RUNNING_SUM,
                        fullPartition, List.of(TypedWindowOrder.asc(WINDOW_SEQ)), WINDOW_DEPT)
                .windowCountAll(RUNNING_COUNT_ALL,
                        fullPartition, List.of(TypedWindowOrder.asc(WINDOW_SEQ)), WINDOW_DEPT)
                .orderBy(WINDOW_DEPT)
                .orderBy(WINDOW_SEQ)
                .filter(sampleWindowMetricInputs(), WindowMetricProjection.class)
                .stream()
                .map(row -> row.department + ":" + row.seq + ":" + row.runningSum + ":" + row.runningCountAll)
                .toList();

        List<String> sqlLikeRows = PojoLensSql
                .parse("select department, seq, amount, "
                        + "sum(amount) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and unbounded following) as runningSum, "
                        + "count(*) over (partition by department order by seq asc "
                        + "rows between unbounded preceding and unbounded following) as runningCountAll "
                        + "order by department asc, seq asc")
                .filter(sampleWindowMetricInputs(), WindowMetricProjection.class)
                .stream()
                .map(row -> row.department + ":" + row.seq + ":" + row.runningSum + ":" + row.runningCountAll)
                .toList();

        assertEquals(sqlLikeRows, typedRows);
    }

    @Test
    void typedJoinAndGroupedCountShouldMatchEquivalentSqlLikeExecution() {
        JoinBindings joinBindings = JoinBindings.of("employees", sampleCompanyEmployees());

        List<String> typedRows = TypedQuery.from(Company.class)
                .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
                .groupBy(JOINED_TITLE)
                .count(JOINED_TOTAL)
                .orderByDesc(JOINED_TOTAL)
                .filter(sampleCompanies(), joinBindings, JoinedTitleCount.class)
                .stream()
                .map(row -> row.title + ":" + row.total)
                .toList();

        List<String> sqlLikeRows = PojoLensSql
                .parse("select title, count(*) as total from companies "
                        + "left join employees on id = companyId group by title order by total desc")
                .filter(sampleCompanies(), joinBindings, JoinedTitleCount.class)
                .stream()
                .map(row -> row.title + ":" + row.total)
                .toList();

        assertEquals(sqlLikeRows, typedRows);
    }

    @Test
    void typedTotalsProjectionShouldSupportCountAndMetricWithoutGroupBy() {
        List<TotalsRow> result = TypedQuery.from(Employee.class)
                .count(TOTAL)
                .metric(SALARY, Metric.SUM, PAYROLL)
                .filter(sampleEmployees(), TotalsRow.class);

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).total);
        assertEquals(450000L, result.get(0).payroll);
    }

    @Test
    void typedHavingShouldSupportTotalsStyleMetrics() {
        List<TotalsRow> result = TypedQuery.from(Employee.class)
                .count(TOTAL)
                .metric(SALARY, Metric.SUM, PAYROLL)
                .having(PAYROLL.gte(400_000L))
                .filter(sampleEmployees(), TotalsRow.class);

        assertEquals(1, result.size());
        assertEquals(4L, result.get(0).total);
        assertEquals(450000L, result.get(0).payroll);
    }

    @Test
    void typedJoinShouldFailWhenJoinSourceBindingIsMissing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TypedQuery.from(Company.class)
                        .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
                        .where(JOINED_TITLE.eq("Engineer"))
                        .filter(sampleCompanies()));

        assertTrue(ex.getMessage().contains("EQ-SQL-VAL-003"));
        assertTrue(ex.getMessage().contains("Missing JOIN source binding for 'employees'"));
    }

    @Test
    void selectShouldFailWhenMixedWithGroupedMetrics() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TypedQuery.from(Employee.class)
                        .select(NAME)
                        .groupBy(DEPT)
                        .count(TOTAL)
                        .filter(sampleEmployees(), DepartmentCount.class));

        assertTrue(ex.getMessage().contains("select(...) cannot be combined with groupBy/count/metric"));
    }

    @Test
    void havingShouldFailWithoutGroupOrMetric() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TypedQuery.from(Employee.class)
                        .having(NAME.eq("Alice"))
                        .filter(sampleEmployees()));

        assertTrue(ex.getMessage().contains("having(...) requires groupBy(...) or count/metric output"));
    }

    @Test
    void havingShouldFailForNonGroupedNonAggregateField() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TypedQuery.from(Employee.class)
                        .groupBy(DEPT)
                        .count(TOTAL)
                        .having(SALARY.gte(100_000))
                        .filter(sampleEmployees(), DepartmentCount.class));

        assertTrue(ex.getMessage().contains("must match a grouped field or metric alias"));
    }

    @Test
    void qualifyShouldFailWithoutWindow() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TypedQuery.from(Employee.class)
                        .qualify(RN.lte(1L))
                        .filter(sampleEmployees()));

        assertTrue(ex.getMessage().contains("qualify(...) requires at least one window output"));
    }

    @Test
    void qualifyShouldFailForNonWindowField() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TypedQuery.from(Employee.class)
                        .window(WindowFunction.ROW_NUMBER, RN, List.of(TypedWindowOrder.desc(SALARY)), DEPT)
                        .qualify(SALARY.gte(100_000))
                        .filter(sampleEmployees(), DepartmentRank.class));

        assertTrue(ex.getMessage().contains("must match a selected window output alias"));
    }

    @Test
    void windowsShouldFailForAggregateShape() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TypedQuery.from(Employee.class)
                        .groupBy(DEPT)
                        .count(TOTAL)
                        .window(WindowFunction.ROW_NUMBER, RN, List.of(TypedWindowOrder.desc(SALARY)), DEPT)
                        .filter(sampleEmployees(), DepartmentRank.class));

        assertTrue(ex.getMessage().contains("windows are only supported for non-aggregate query shapes"));
    }

    @Test
    void notPredicateThrowsUnsupportedOperationException() {
        TypedQuery<Employee> q = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true).not());
        assertThrows(UnsupportedOperationException.class, () -> q.filter(sampleEmployees()));
    }

    // --- Guard interop ---

    @Test
    void guardBlocksWhenRowCountExceedsScanLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsScanned(2).build();
        TypedQuery<Employee> q = TypedQuery.from(Employee.class).executionGuard(guard);
        assertThrows(QueryExecutionGuardException.class, () -> q.filter(sampleEmployees()));
    }

    @Test
    void guardBlocksWhenResultExceedsReturnedLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsReturned(1).build();
        TypedQuery<Employee> q = TypedQuery.from(Employee.class)
                .where(ACTIVE.eq(true))
                .executionGuard(guard);
        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> q.filter(sampleEmployees()));
        assertEquals("GUARD_ROWS_RETURNED_EXCEEDED", ex.outcome().blockCode());
    }

    @Test
    void unrestrictedGuardAllowsExecution() {
        QueryExecutionGuard guard = QueryExecutionGuard.unrestricted();
        List<Employee> result = TypedQuery.from(Employee.class)
                .executionGuard(guard)
                .filter(sampleEmployees());
        assertEquals(4, result.size());
    }

    @Test
    void guardIsCarriedThroughFluentChain() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsScanned(2).build();
        TypedQuery<Employee> q = TypedQuery.from(Employee.class)
                .executionGuard(guard)
                .where(ACTIVE.eq(true))
                .orderByDesc(SALARY)
                .limit(1);
        assertThrows(QueryExecutionGuardException.class, () -> q.filter(sampleEmployees()));
    }

    // --- Explain interop ---

    @Test
    void explainReturnsNonNullMap() {
        Map<String, Object> plan = TypedQuery.from(Employee.class)
                .where(SALARY.gt(100_000))
                .orderByDesc(SALARY)
                .limit(5)
                .explain(sampleEmployees());
        assertNotNull(plan);
    }

    @Test
    void explainOnEmptyRowsReturnsMap() {
        Map<String, Object> plan = TypedQuery.from(Employee.class)
                .where(NAME.eq("Alice"))
                .explain(List.of());
        assertNotNull(plan);
    }

    @Test
    void explainWithDatasetBundleReturnsMap() {
        DatasetBundle bundle = DatasetBundle.of(
                sampleCompanies(),
                JoinBindings.of("employees", sampleCompanyEmployees())
        );

        Map<String, Object> plan = TypedQuery.from(Company.class)
                .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
                .where(JOINED_TITLE.eq("Engineer"))
                .explain(bundle);

        assertNotNull(plan);
    }

    @Test
    void explainWithGroupedMetricsReturnsMap() {
        Map<String, Object> plan = TypedQuery.from(Employee.class)
                .groupBy(DEPT)
                .count(TOTAL)
                .metric(SALARY, Metric.SUM, TOTAL_SALARY)
                .explain(sampleEmployees());

        assertNotNull(plan);
    }

    @Test
    void explainWithWindowQualifyReturnsMap() {
        Map<String, Object> plan = TypedQuery.from(Employee.class)
                .window(WindowFunction.ROW_NUMBER, RN, List.of(TypedWindowOrder.desc(SALARY)), DEPT)
                .qualify(RN.lte(1L))
                .explain(sampleEmployees());

        assertNotNull(plan);
    }

    // --- Schema interop ---

    @Test
    void schemaReturnsNonNull() {
        TabularSchema s = TypedQuery.from(Employee.class)
                .where(SALARY.gt(100_000))
                .schema(sampleEmployees());
        assertNotNull(s);
    }

    @Test
    void schemaWithProjectionClassReturnsNonNull() {
        TabularSchema s = TypedQuery.from(Employee.class)
                .schema(sampleEmployees(), Employee.class);
        assertNotNull(s);
    }

    @Test
    void schemaWithJoinBindingsReturnsNonNull() {
        TabularSchema s = TypedQuery.from(Company.class)
                .join("employees", COMPANY_ID, EMPLOYEE_COMPANY_ID, Join.LEFT_JOIN)
                .where(JOINED_TITLE.eq("Engineer"))
                .schema(sampleCompanies(), JoinBindings.of("employees", sampleCompanyEmployees()));

        assertNotNull(s);
    }

    @Test
    void schemaWithGroupedMetricsReflectsGroupedProjection() {
        TabularSchema s = TypedQuery.from(Employee.class)
                .groupBy(DEPT)
                .count(EMPLOYEE_COUNT)
                .metric(SALARY, Metric.SUM, TOTAL_SALARY)
                .schema(sampleEmployees(), DepartmentAgg.class);

        assertEquals(List.of("department", "employeeCount", "totalSalary"), s.names());
    }

    @Test
    void schemaWithWindowProjectionReflectsWindowOutput() {
        TabularSchema s = TypedQuery.from(Employee.class)
                .window(WindowFunction.ROW_NUMBER, RN, List.of(TypedWindowOrder.desc(SALARY)), DEPT)
                .schema(sampleEmployees(), DepartmentRank.class);

        assertEquals(List.of("department", "name", "salary", "rn"), s.names());
    }

    // --- Helpers ---

    private static Method requirePublicMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method m = type.getMethod(name, params);
        assertTrue(Modifier.isPublic(m.getModifiers()),
                () -> "Expected public: " + type.getSimpleName() + "." + name);
        return m;
    }

    private static Method requirePublicStaticMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method m = requirePublicMethod(type, name, params);
        assertTrue(Modifier.isStatic(m.getModifiers()),
                () -> "Expected static: " + type.getSimpleName() + "." + name);
        return m;
    }

    public static class TotalsRow {
        public long total;
        public long payroll;

        public TotalsRow() {
        }
    }

    public static class JoinedTitleCount {
        public String title;
        public long total;

        public JoinedTitleCount() {
        }
    }
}
