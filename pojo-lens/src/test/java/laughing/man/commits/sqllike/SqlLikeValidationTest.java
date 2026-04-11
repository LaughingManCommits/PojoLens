package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.annotations.Exclude;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.SqlLikeProjectionFixtures.ComputedScalarProjection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;

public class SqlLikeValidationTest {

    @Test
    public void unknownWhereFieldShouldBeRejected() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1)
        );
        try {
            PojoLensSql.parse("where missingField = 'abc'").filter(source, Foo.class);
            fail("Expected unknown field validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'missingField'"));
            assertTrue(ex.getMessage().contains("in WHERE clause"));
        }
    }

    @Test
    public void unknownFieldShouldIncludeSuggestionWhenCloseMatchExists() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1)
        );
        try {
            PojoLensSql.parse("where integeField >= 1").filter(source, Foo.class);
            fail("Expected unknown field validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'integeField'"));
            assertTrue(ex.getMessage().contains("Did you mean"));
            assertTrue(ex.getMessage().contains("integerField"));
            assertTrue(ex.getMessage().contains("in WHERE clause"));
        }
    }

    @Test
    public void unknownFieldShouldIncludeDeterministicMultipleSuggestions() {
        List<SuggestionBean> source = Arrays.asList(
                new SuggestionBean("open", "alpha", "ok")
        );
        try {
            PojoLensSql.parse("where stte = 'open'").filter(source, SuggestionBean.class);
            fail("Expected unknown field validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'stte'"));
            assertTrue(ex.getMessage().contains("Did you mean one of [state, stage"));
            assertTrue(ex.getMessage().contains("in WHERE clause"));
        }
    }

    @Test
    public void unknownFieldShouldNotIncludeSuggestionWhenNoCloseMatchExists() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1)
        );
        try {
            PojoLensSql.parse("where qzxv = 1").filter(source, Foo.class);
            fail("Expected unknown field validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'qzxv'"));
            assertFalse(ex.getMessage().contains("Did you mean"));
            assertTrue(ex.getMessage().contains("in WHERE clause"));
        }
    }

    @Test
    public void excludedFieldShouldBeRejected() {
        List<SecureBean> source = Arrays.asList(
                new SecureBean("Alice", "token-a"),
                new SecureBean("Bob", "token-b")
        );
        try {
            PojoLensSql.parse("where internalToken = 'token-a'").filter(source, SecureBean.class);
            fail("Expected excluded field validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'internalToken'"));
        }
    }

    @Test
    public void parserShouldRejectTooLongQuery() {
        String longLiteral = "x".repeat(4500);
        try {
            PojoLensSql.parse("where stringField = '" + longLiteral + "'");
            fail("Expected query length validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Query exceeds maximum length"));
        }
    }

    @Test
    public void parserShouldRejectTooManyWherePredicates() {
        StringBuilder sb = new StringBuilder("where integerField = 1");
        for (int i = 0; i < 110; i++) {
            sb.append(" and integerField = 1");
        }
        try {
            PojoLensSql.parse(sb.toString());
            fail("Expected WHERE predicate limit error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Too many WHERE predicates"));
        }
    }

    @Test
    public void parserShouldRejectTooManyHavingPredicates() {
        StringBuilder sb = new StringBuilder("select department, count(*) as total group by department having total = 1");
        for (int i = 0; i < 110; i++) {
            sb.append(" or total = 1");
        }
        try {
            PojoLensSql.parse(sb.toString());
            fail("Expected HAVING predicate limit error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Too many HAVING predicates"));
        }
    }

    @Test
    public void parserShouldRejectTooManyOrderFields() {
        StringBuilder sb = new StringBuilder("where integerField = 1 order by integerField");
        for (int i = 0; i < 30; i++) {
            sb.append(", stringField");
        }
        try {
            PojoLensSql.parse(sb.toString());
            fail("Expected ORDER BY field limit error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Too many ORDER BY fields"));
        }
    }

    @Test
    public void aggregationSelectFieldMustBeInGroupByWhenNotAggregated() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select department, name, count(*) as total group by department")
                    .filter(employees, Employee.class);
            fail("Expected GROUP BY validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("must be present in GROUP BY"));
        }
    }

    @Test
    public void groupByWithoutAggregateShouldBeRejected() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select department group by department")
                    .filter(employees, Employee.class);
            fail("Expected GROUP BY aggregate requirement error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("GROUP BY requires at least one aggregate function"));
        }
    }

    @Test
    public void aggregatedOrderByMustReferenceGroupOrMetricOutput() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select department, count(*) as total group by department order by salary desc")
                    .filter(employees, AggregationProjection.class);
            fail("Expected ORDER BY validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'salary'"));
            assertTrue(ex.getMessage().contains("in ORDER BY clause"));
        }
    }

    @Test
    public void aliasingGroupedNonAggregatedFieldShouldBeAllowed() {
        List<Employee> employees = sampleEmployees();
        List<GroupedAliasProjection> rows = PojoLensSql.parse("select department as dept, count(*) as total group by dept having dept = 'Engineering'")
                .filter(employees, GroupedAliasProjection.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals(3L, rows.get(0).total);
    }

    @Test
    public void bucketFunctionShouldRequireSupportedDateTimeField() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select bucket(name,'month') as period, count(*) as total group by period")
                    .filter(employees, AggregationProjection.class);
            fail("Expected bucket date-field validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Time bucket requires supported date/time field"));
        }
    }

    @Test
    public void bucketFunctionShouldAllowInstantField() {
        List<InstantBucketRow> rows = List.of(
                new InstantBucketRow(Instant.parse("2025-02-01T00:00:00Z"))
        );

        List<PeriodProjection> result = PojoLensSql.parse("select bucket(hireDate,'month') as period, count(*) as total group by period")
                .filter(rows, PeriodProjection.class);

        assertEquals(1, result.size());
        assertEquals("2025-02", result.get(0).period);
        assertEquals(1L, result.get(0).total);
    }

    @Test
    public void bucketFunctionShouldAllowLocalDateField() {
        List<LocalDateBucketRow> rows = List.of(
                new LocalDateBucketRow(LocalDate.of(2025, 2, 1))
        );

        List<PeriodProjection> result = PojoLensSql.parse("select bucket(hireDate,'month') as period, count(*) as total group by period")
                .filter(rows, PeriodProjection.class);

        assertEquals(1, result.size());
        assertEquals("2025-02", result.get(0).period);
        assertEquals(1L, result.get(0).total);
    }

    @Test
    public void havingShouldRequireGroupedOrAggregatedContext() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select department having department = 'Engineering'")
                    .filter(employees, Employee.class);
            fail("Expected HAVING context validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("HAVING requires GROUP BY or aggregate SELECT output"));
        }
    }

    @Test
    public void unknownHavingReferenceShouldBeRejected() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select department, count(*) as total group by department having missingField > 1")
                    .filter(employees, AggregationProjection.class);
            fail("Expected unknown HAVING reference error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown HAVING reference 'missingField'"));
        }
    }

    @Test
    public void nonGroupedNonAggregatedHavingFieldShouldBeRejected() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select department, count(*) as total group by department having salary > 100000")
                    .filter(employees, AggregationProjection.class);
            fail("Expected invalid HAVING reference error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Invalid HAVING reference 'salary'"));
        }
    }

    @Test
    public void ambiguousHavingReferenceShouldBeRejected() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select sum(salary) as department group by department having department > 10")
                    .filter(employees, AggregationProjection.class);
            fail("Expected ambiguous HAVING reference error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Ambiguous HAVING reference 'department'"));
        }
    }

    @Test
    public void havingShouldAllowAggregateExpressionReference() {
        List<Employee> employees = sampleEmployees();
        PojoLensSql.parse("select department, count(*) as total group by department having count(*) >= 1")
                .filter(employees, AggregationProjection.class);
    }

    @Test
    public void havingShouldAllowUnselectedAggregateExpression() {
        List<Employee> employees = sampleEmployees();
        List<AggregationProjection> rows = PojoLensSql.parse("select department, count(*) as total group by department having sum(salary) > 100000")
                .filter(employees, AggregationProjection.class);
        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(3L, rows.get(0).total);
    }

    @Test
    public void aggregatedOrderByShouldAllowUnselectedAggregateExpression() {
        List<Employee> employees = sampleEmployees();
        List<GroupedAliasProjection> rows = PojoLensSql.parse("select department as dept, count(*) as total group by dept order by sum(salary) desc")
                .filter(employees, GroupedAliasProjection.class);

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).dept);
        assertEquals(3L, rows.get(0).total);
        assertEquals("Finance", rows.get(1).dept);
    }

    @Test
    public void computedSelectExpressionShouldRejectUnknownIdentifier() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1)
        );
        try {
            PojoLensSql.parse("select missing + 1 as x where integerField >= 1")
                    .filter(source, ComputedScalarProjection.class);
            fail("Expected expression identifier validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'missing'"));
            assertTrue(ex.getMessage().contains("SELECT"));
        }
    }

    @Test
    public void computedSelectExpressionShouldRejectAggregateQueryContext() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select sum(salary) as total, total / 2 as halfTotal group by department")
                    .filter(employees, AggregationProjection.class);
            fail("Expected computed aggregate select validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Computed SELECT expressions are only supported for non-aggregate queries"));
        }
    }

    @Test
    public void subqueryWithSingleAggregateSelectShouldBeAllowed() {
        List<Employee> employees = sampleEmployees();
        PojoLensSql.parse("where id in (select count(*) as total where active = true)")
                .filter(employees, Employee.class);
    }

    @Test
    public void subqueryWithGroupedFieldAndHavingShouldBeAllowed() {
        List<Employee> employees = sampleEmployees();
        PojoLensSql.parse("where department in (select department group by department having count(*) > 1)")
                .filter(employees, Employee.class);
    }

    @Test
    public void subqueryShouldRejectJoinClause() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("where department in (select department from employees join companies on companyId = id)")
                    .filter(employees, Employee.class);
            fail("Expected JOIN subquery validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Subqueries do not support JOIN clauses in v1"));
        }
    }

    @Test
    public void subqueryShouldBeRejectedInHaving() {
        List<Employee> employees = sampleEmployees();
        try {
            PojoLensSql.parse("select department, count(*) as total group by department "
                            + "having department in (select department where active = true)")
                    .filter(employees, AggregationProjection.class);
            fail("Expected HAVING subquery validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Subqueries are only supported in WHERE IN (...) filters"));
        }
    }

    @Test
    public void namedSubquerySourceShouldRequireJoinSourceBinding() {
        List<Company> companies = sampleCompanies();
        try {
            PojoLensSql.parse("where id in (select companyId from employees where title = 'Engineer')")
                    .filter(companies, JoinBindings.empty(), Company.class);
            fail("Expected missing subquery source binding error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Missing subquery source binding for 'employees'"));
        }
    }

    public static class SecureBean {
        String name;
        @Exclude
        String internalToken;

        public SecureBean() {
        }

        public SecureBean(String name, String internalToken) {
            this.name = name;
            this.internalToken = internalToken;
        }
    }

    public static class AggregationProjection {
        String department;
        long total;

        public AggregationProjection() {
        }
    }

    public static class GroupedAliasProjection {
        String dept;
        long total;

        public GroupedAliasProjection() {
        }
    }

    public static class SuggestionBean {
        String state;
        String stage;
        String status;

        public SuggestionBean() {
        }

        public SuggestionBean(String state, String stage, String status) {
            this.state = state;
            this.stage = stage;
            this.status = status;
        }
    }

    public static class PeriodProjection {
        String period;
        long total;

        public PeriodProjection() {
        }
    }

    public static class InstantBucketRow {
        Instant hireDate;

        public InstantBucketRow(Instant hireDate) {
            this.hireDate = hireDate;
        }
    }

    public static class LocalDateBucketRow {
        LocalDate hireDate;

        public LocalDateBucketRow(LocalDate hireDate) {
            this.hireDate = hireDate;
        }
    }
}







