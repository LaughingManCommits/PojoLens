package laughing.man.commits;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.testing.FluentSqlLikeParity;
import laughing.man.commits.builder.QueryRule;
import laughing.man.commits.builder.QueryWindowOrder;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;

public class SqlLikeMappingParityTest {

    @Test
    public void sqlLikeWhereOrderLimitShouldMatchFluentPipeline() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1),
                new Foo("abc", new Date(), 9),
                new Foo("abc", new Date(), 4),
                new Foo("xyz", new Date(), 2)
        );

        List<Foo> fluent = PojoLens.newQueryBuilder(source)
                .addRule("stringField", "abc", Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 1, Clauses.BIGGER, Separator.AND)
                .addOrder("integerField", 1)
                .limit(2)
                .initFilter()
                .filter(Sort.DESC, Foo.class);

        List<Foo> sqlLike = PojoLens
                .parse("select * from rows where stringField = 'abc' and integerField > 1 "
                        + "order by integerField desc limit 2")
                .filter(source, Foo.class);

        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike,
                row -> row.getStringField() + ":" + row.getIntegerField());
    }

    @Test
    public void sqlLikeWhereOrderLimitOffsetShouldMatchFluentPipeline() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1),
                new Foo("abc", new Date(), 9),
                new Foo("abc", new Date(), 4),
                new Foo("xyz", new Date(), 2)
        );

        List<Foo> fluent = PojoLens.newQueryBuilder(source)
                .addRule("stringField", "abc", Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 1, Clauses.BIGGER, Separator.AND)
                .addOrder("integerField", 1)
                .offset(1)
                .limit(1)
                .initFilter()
                .filter(Sort.DESC, Foo.class);

        List<Foo> sqlLike = PojoLens
                .parse("select * from rows where stringField = 'abc' and integerField > 1 "
                        + "order by integerField desc limit 1 offset 1")
                .filter(source, Foo.class);

        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike,
                row -> row.getStringField() + ":" + row.getIntegerField());
        assertEquals(1, sqlLike.size());
        assertEquals(4, sqlLike.get(0).getIntegerField());
    }

    @Test
    public void sqlLikeSelectProjectionShouldMatchFluentProjection() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1),
                new Foo("xyz", new Date(), 2)
        );

        List<Foo> fluent = PojoLens.newQueryBuilder(source)
                .addRule("integerField", 1, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("stringField")
                .initFilter()
                .filter(Foo.class);

        List<Foo> sqlLike = PojoLens
                .parse("select stringField where integerField >= 1")
                .filter(source, Foo.class);

        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike, Foo::getStringField);
        assertEquals(0, sqlLike.get(0).getIntegerField());
    }

    @Test
    public void mixedOrderDirectionsShouldBeRejected() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1),
                new Foo("xyz", new Date(), 2)
        );

        try {
            PojoLens.parse("where integerField >= 1 order by stringField asc, integerField desc")
                    .filter(source, Foo.class);
            fail("Expected mixed ORDER BY directions to fail");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Mixed ORDER BY directions are not supported in v1"));
        }
    }

    @Test
    public void sqlLikeGroupedAggregationShouldMatchFluentPipeline() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentAgg> fluent = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addCount("employeeCount")
                .addMetric("salary", Metric.SUM, "totalSalary")
                .initFilter()
                .filter(DepartmentAgg.class);

        List<DepartmentAgg> sqlLike = PojoLens
                .parse("select department, count(*) as employeeCount, sum(salary) as totalSalary "
                        + "group by department")
                .filter(employees, DepartmentAgg.class);

        FluentSqlLikeParity.assertUnorderedEquals(fluent, sqlLike,
                row -> row.department + ":" + row.employeeCount + ":" + row.totalSalary);
    }

    @Test
    public void sqlLikeGroupedOrderByMetricAliasShouldMatchFluentPipeline() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentAgg> fluent = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addCount("employeeCount")
                .addMetric("salary", Metric.SUM, "totalSalary")
                .addOrder("totalSalary", 1)
                .limit(1)
                .initFilter()
                .filter(Sort.DESC, DepartmentAgg.class);

        List<DepartmentAgg> sqlLike = PojoLens
                .parse("select department, count(*) as employeeCount, sum(salary) as totalSalary "
                        + "group by department order by totalSalary desc limit 1")
                .filter(employees, DepartmentAgg.class);

        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike,
                row -> row.department + ":" + row.employeeCount + ":" + row.totalSalary);
        assertEquals(1, sqlLike.size());
        assertEquals("Engineering", sqlLike.get(0).department);
        assertEquals(360000L, sqlLike.get(0).totalSalary);
    }

    @Test
    public void sqlLikeHavingAggregateExpressionShouldMatchFluentAliasBasedHaving() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentAgg> fluent = PojoLens.newQueryBuilder(employees)
                .addGroup("department")
                .addCount("employeeCount")
                .addMetric("salary", Metric.SUM, "hiddenTotalSalary")
                .addHaving("hiddenTotalSalary", 100000, Clauses.BIGGER)
                .initFilter()
                .filter(DepartmentAgg.class);

        List<DepartmentAgg> sqlLike = PojoLens
                .parse("select department, count(*) as employeeCount group by department having sum(salary) > 100000")
                .filter(employees, DepartmentAgg.class);

        FluentSqlLikeParity.assertUnorderedEquals(fluent, sqlLike,
                row -> row.department + ":" + row.employeeCount);
        assertEquals(1, sqlLike.size());
        assertEquals("Engineering", sqlLike.get(0).department);
        assertEquals(3L, sqlLike.get(0).employeeCount);
    }

    @Test
    public void sqlLikeParenthesizedWhereShouldMatchFluentGroupedRules() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1),
                new Foo("abc", new Date(), 7),
                new Foo("xyz", new Date(), 6),
                new Foo("xyz", new Date(), 2)
        );

        List<Foo> fluent = PojoLens.newQueryBuilder(source)
                .allOf(
                        QueryRule.of("stringField", "abc", Clauses.EQUAL),
                        QueryRule.of("integerField", 5, Clauses.BIGGER_EQUAL)
                )
                .allOf(
                        QueryRule.of("stringField", "xyz", Clauses.EQUAL),
                        QueryRule.of("integerField", 5, Clauses.BIGGER_EQUAL)
                )
                .initFilter()
                .filter(Foo.class);

        List<Foo> sqlLike = PojoLens
                .parse("where (stringField = 'abc' or stringField = 'xyz') and integerField >= 5")
                .filter(source, Foo.class);

        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike,
                row -> row.getStringField() + ":" + row.getIntegerField());
    }

    @Test
    public void sqlLikeArithmeticWhereExpressionShouldMatchEquivalentFluentRule() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1),
                new Foo("abc", new Date(), 5),
                new Foo("xyz", new Date(), 7)
        );

        List<Foo> fluent = PojoLens.newQueryBuilder(source)
                .addRule("integerField", 4, Clauses.BIGGER_EQUAL, Separator.AND)
                .initFilter()
                .filter(Foo.class);

        List<Foo> sqlLike = PojoLens
                .parse("where integerField - 1 >= 3")
                .filter(source, Foo.class);

        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike,
                row -> row.getStringField() + ":" + row.getIntegerField());
    }

    @Test
    public void sqlLikeWindowQualifyShouldMatchFluentPipeline() {
        List<Employee> employees = sampleEmployees();

        List<DepartmentRank> fluent = PojoLens.newQueryBuilder(employees)
                .addRule("active", true, Clauses.EQUAL)
                .addWindow(
                        "rn",
                        WindowFunction.ROW_NUMBER,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("salary", Sort.DESC))
                )
                .addQualify("rn", 1, Clauses.SMALLER_EQUAL)
                .addOrder("department", 1)
                .addOrder("rn", 2)
                .initFilter()
                .filter(Sort.ASC, DepartmentRank.class);

        List<DepartmentRank> sqlLike = PojoLens
                .parse("select department, name, salary, "
                        + "row_number() over (partition by department order by salary desc) as rn "
                        + "where active = true qualify rn <= 1 order by department asc, rn asc")
                .filter(employees, DepartmentRank.class);

        FluentSqlLikeParity.assertOrderedEquals(fluent, sqlLike,
                row -> row.department + ":" + row.name + ":" + row.salary + ":" + row.rn);
    }

    public static class DepartmentAgg {
        public String department;
        public long employeeCount;
        public long totalSalary;

        public DepartmentAgg() {
        }
    }

    public static class DepartmentRank {
        public String department;
        public String name;
        public int salary;
        public long rn;

        public DepartmentRank() {
        }
    }
}

