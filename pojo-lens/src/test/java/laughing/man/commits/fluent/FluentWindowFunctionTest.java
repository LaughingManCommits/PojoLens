package laughing.man.commits.fluent;

import laughing.man.commits.PojoLensCore;

import laughing.man.commits.builder.QueryWindowOrder;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.testutil.WindowTestFixtures.DepartmentAgg;
import laughing.man.commits.testutil.WindowTestFixtures.DepartmentRank;
import laughing.man.commits.testutil.WindowTestFixtures.WindowMetricInput;
import laughing.man.commits.testutil.WindowTestFixtures.WindowMetricProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static laughing.man.commits.testutil.WindowTestFixtures.sampleWindowMetricInputs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FluentWindowFunctionTest {

    @Test
    public void fluentWindowQualifyShouldReturnTopPerDepartment() {
        List<DepartmentRank> rows = PojoLensCore.newQueryBuilder(sampleEmployees())
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
                () -> PojoLensCore.newQueryBuilder(sampleEmployees())
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
                () -> PojoLensCore.newQueryBuilder(sampleEmployees())
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
                () -> PojoLensCore.newQueryBuilder(sampleEmployees())
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

    @Test
    public void fluentAggregateWindowsShouldComputeRunningMetricsWithNullParity() {
        List<WindowMetricProjection> rows = PojoLensCore.newQueryBuilder(sampleWindowMetricInputs())
                .addWindow(
                        "runningSum",
                        WindowFunction.SUM,
                        "amount",
                        false,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("seq", Sort.ASC))
                )
                .addWindow(
                        "runningCount",
                        WindowFunction.COUNT,
                        "amount",
                        false,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("seq", Sort.ASC))
                )
                .addWindow(
                        "runningCountAll",
                        WindowFunction.COUNT,
                        null,
                        true,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("seq", Sort.ASC))
                )
                .addWindow(
                        "runningAvg",
                        WindowFunction.AVG,
                        "amount",
                        false,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("seq", Sort.ASC))
                )
                .addWindow(
                        "runningMin",
                        WindowFunction.MIN,
                        "amount",
                        false,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("seq", Sort.ASC))
                )
                .addWindow(
                        "runningMax",
                        WindowFunction.MAX,
                        "amount",
                        false,
                        List.of("department"),
                        List.of(QueryWindowOrder.of("seq", Sort.ASC))
                )
                .addOrder("department", 1)
                .addOrder("seq", 2)
                .initFilter()
                .filter(Sort.ASC, WindowMetricProjection.class);

        assertEquals(6, rows.size());

        assertEquals("A", rows.get(0).department);
        assertEquals(10L, rows.get(0).runningSum);
        assertEquals(1L, rows.get(0).runningCount);
        assertEquals(1L, rows.get(0).runningCountAll);
        assertEquals(10D, rows.get(0).runningAvg);
        assertEquals(10, rows.get(0).runningMin);
        assertEquals(10, rows.get(0).runningMax);

        assertEquals("A", rows.get(1).department);
        assertEquals(10L, rows.get(1).runningSum);
        assertEquals(1L, rows.get(1).runningCount);
        assertEquals(2L, rows.get(1).runningCountAll);
        assertEquals(10D, rows.get(1).runningAvg);
        assertEquals(10, rows.get(1).runningMin);
        assertEquals(10, rows.get(1).runningMax);

        assertEquals("A", rows.get(2).department);
        assertEquals(15L, rows.get(2).runningSum);
        assertEquals(2L, rows.get(2).runningCount);
        assertEquals(3L, rows.get(2).runningCountAll);
        assertEquals(7.5D, rows.get(2).runningAvg);
        assertEquals(5, rows.get(2).runningMin);
        assertEquals(10, rows.get(2).runningMax);

        assertEquals("B", rows.get(3).department);
        assertEquals(2L, rows.get(3).runningSum);
        assertEquals(1L, rows.get(3).runningCount);
        assertEquals(1L, rows.get(3).runningCountAll);
        assertEquals(2D, rows.get(3).runningAvg);
        assertEquals(2, rows.get(3).runningMin);
        assertEquals(2, rows.get(3).runningMax);

        assertEquals("B", rows.get(4).department);
        assertEquals(5L, rows.get(4).runningSum);
        assertEquals(2L, rows.get(4).runningCount);
        assertEquals(2L, rows.get(4).runningCountAll);
        assertEquals(2.5D, rows.get(4).runningAvg);
        assertEquals(2, rows.get(4).runningMin);
        assertEquals(3, rows.get(4).runningMax);

        assertEquals("C", rows.get(5).department);
        assertNull(rows.get(5).runningSum);
        assertEquals(0L, rows.get(5).runningCount);
        assertEquals(1L, rows.get(5).runningCountAll);
        assertNull(rows.get(5).runningAvg);
        assertNull(rows.get(5).runningMin);
        assertNull(rows.get(5).runningMax);
    }

    @Test
    public void fluentAggregateWindowShouldRejectNonNumericValueField() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PojoLensCore.newQueryBuilder(List.of(new WindowMetricInput("A", 1, 10)))
                        .addWindow(
                                "invalid",
                                WindowFunction.SUM,
                                "department",
                                false,
                                List.of("department"),
                                List.of(QueryWindowOrder.of("seq", Sort.ASC))
                        )
        );
        assertTrue(ex.getMessage().contains("requires numeric field"));
    }
}



