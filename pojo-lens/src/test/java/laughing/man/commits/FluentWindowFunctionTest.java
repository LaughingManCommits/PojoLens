package laughing.man.commits;

import laughing.man.commits.builder.QueryWindowOrder;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.WindowFunction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FluentWindowFunctionTest {

    @Test
    public void fluentWindowQualifyShouldReturnTopPerDepartment() {
        List<DepartmentRank> rows = PojoLens.newQueryBuilder(sampleEmployees())
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

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals("Cara", rows.get(0).name);
        assertEquals(1L, rows.get(0).rn);
        assertEquals("Finance", rows.get(1).department);
        assertEquals("Bob", rows.get(1).name);
        assertEquals(1L, rows.get(1).rn);
    }

    @Test
    public void fluentQualifyShouldRejectUnknownReference() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLens.newQueryBuilder(sampleEmployees())
                        .addRule("active", true, Clauses.EQUAL)
                        .addWindow(
                                "rn",
                                WindowFunction.ROW_NUMBER,
                                List.of("department"),
                                List.of(QueryWindowOrder.of("salary", Sort.DESC))
                        )
                        .addQualify("missingRank", 1, Clauses.SMALLER_EQUAL)
                        .initFilter()
                        .filter(DepartmentRank.class)
        );
        assertTrue(ex.getMessage().contains("Unknown field 'missingRank' in QUALIFY clause"));
    }

    @Test
    public void fluentQualifyWithoutWindowShouldFail() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLens.newQueryBuilder(sampleEmployees())
                        .addRule("active", true, Clauses.EQUAL)
                        .addQualify("rn", 1, Clauses.SMALLER_EQUAL)
                        .initFilter()
                        .filter(DepartmentRank.class)
        );
        assertTrue(ex.getMessage().contains("QUALIFY requires at least one window output"));
    }

    @Test
    public void fluentWindowShouldRejectAggregateShape() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLens.newQueryBuilder(sampleEmployees())
                        .addGroup("department")
                        .addMetric("salary", Metric.SUM, "totalSalary")
                        .addWindow(
                                "rn",
                                WindowFunction.ROW_NUMBER,
                                List.of("department"),
                                List.of(QueryWindowOrder.of("salary", Sort.DESC))
                        )
                        .initFilter()
                        .filter(DepartmentAgg.class)
        );
        assertTrue(ex.getMessage().contains("Window functions are only supported for non-aggregate fluent queries"));
    }

    public static class DepartmentRank {
        public String department;
        public String name;
        public int salary;
        public long rn;

        public DepartmentRank() {
        }
    }

    public static class DepartmentAgg {
        public String department;
        public long totalSalary;

        public DepartmentAgg() {
        }
    }
}
