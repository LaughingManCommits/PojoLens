package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLens;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.builder.QueryWindowOrder;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.PublicApiModels.DepartmentMetricInput;
import laughing.man.commits.testutil.PublicApiModels.DepartmentMetricResult;
import laughing.man.commits.testutil.PublicApiModels.JoinChild;
import laughing.man.commits.testutil.PublicApiModels.JoinParent;
import laughing.man.commits.testutil.PublicApiModels.JoinProjection;
import laughing.man.commits.testutil.PublicApiModels.WindowAggregateApiRow;
import laughing.man.commits.testutil.PublicApiModels.WindowAggregateInput;
import laughing.man.commits.testutil.PublicApiModels.WindowRankRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PublicApiFluentCoverageTest extends AbstractPublicApiCoverageTest {

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
    public void fluentAggregateWindowOverloadShouldBeUsableFromPublicApi() {
        List<WindowAggregateInput> source = Arrays.asList(
                new WindowAggregateInput("A", 1, 10),
                new WindowAggregateInput("A", 2, 5),
                new WindowAggregateInput("B", 1, 3)
        );

        List<WindowAggregateApiRow> rows = PojoLens.newQueryBuilder(source)
                .addWindow(
                        "runningSum",
                        WindowFunction.SUM,
                        "amount",
                        false,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("seq", Sort.ASC))
                )
                .addWindow(
                        "runningRows",
                        WindowFunction.COUNT,
                        null,
                        true,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("seq", Sort.ASC))
                )
                .addOrder("department", 1)
                .addOrder("seq", 2)
                .initFilter()
                .filter(Sort.ASC, WindowAggregateApiRow.class);

        assertEquals(3, rows.size());
        assertEquals(10L, rows.get(0).runningSum);
        assertEquals(1L, rows.get(0).runningRows);
        assertEquals(15L, rows.get(1).runningSum);
        assertEquals(2L, rows.get(1).runningRows);
        assertEquals(3L, rows.get(2).runningSum);
        assertEquals(1L, rows.get(2).runningRows);
    }

    @Test
    public void fluentStreamingControlsShouldBeUsableFromPublicApi() {
        List<String> fluentNames = PojoLens.newQueryBuilder(sampleEmployees())
                .addRule("active", true, Clauses.EQUAL)
                .limit(2)
                .initFilter()
                .stream(Employee.class)
                .map(row -> row.name)
                .toList();

        assertEquals(List.of("Alice", "Bob"), fluentNames);
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
}
