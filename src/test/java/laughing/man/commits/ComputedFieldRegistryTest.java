package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.report.ReportDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComputedFieldRegistryTest {

    @Test
    public void sqlLikeQueryShouldSupportRegisteredComputedFields() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build();

        List<AdjustedSalaryRow> rows = PojoLens
                .parse("select name, adjustedSalary where adjustedSalary >= :min order by adjustedSalary desc")
                .computedFields(registry)
                .strictParameterTypes()
                .params(Map.of("min", 120000.0))
                .filter(sampleEmployees(), AdjustedSalaryRow.class);

        assertEquals(3, rows.size());
        assertEquals("Cara", rows.get(0).name);
        assertEquals(143000.0, rows.get(0).adjustedSalary, 0.0001);
    }

    @Test
    public void fluentBuilderShouldSupportRegisteredComputedFieldsForRulesAndMetrics() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build();

        List<DepartmentAdjustedPayrollRow> rows = PojoLens.newQueryBuilder(sampleEmployees())
                .computedFields(registry)
                .addRule("adjustedSalary", 120000.0, Clauses.BIGGER_EQUAL)
                .addGroup("department")
                .addMetric("adjustedSalary", Metric.SUM, "totalAdjustedPayroll")
                .addOrder("totalAdjustedPayroll", 1)
                .initFilter()
                .filter(DepartmentAdjustedPayrollRow.class);

        assertEquals(1, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(396000.0, rows.get(0).totalAdjustedPayroll, 0.0001);
    }

    @Test
    public void runtimeShouldApplyComputedFieldRegistryToParsedQueriesAndBuilders() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("salaryDelta", "salary - 100000", Double.class)
                .build();
        PojoLensRuntime runtime = PojoLens.newRuntime();
        runtime.setComputedFieldRegistry(registry);

        List<SalaryDeltaRow> sqlRows = runtime
                .parse("select name, salaryDelta where salaryDelta > 0 order by salaryDelta desc")
                .filter(sampleEmployees(), SalaryDeltaRow.class);

        List<SalaryDeltaRow> fluentRows = runtime.newQueryBuilder(sampleEmployees())
                .addRule("salaryDelta", 0.0, Clauses.BIGGER, Separator.AND)
                .addField("name")
                .addField("salaryDelta")
                .initFilter()
                .filter(SalaryDeltaRow.class);

        assertEquals(3, sqlRows.size());
        assertEquals(3, fluentRows.size());
        assertEquals("Cara", sqlRows.get(0).name);
        assertEquals("Alice", fluentRows.get(0).name);
    }

    @Test
    public void fluentBuilderShouldAllowComputedMetricValidationWithoutSourceRows() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build();

        assertDoesNotThrow(() -> PojoLens.newQueryBuilder(List.of())
                .computedFields(registry)
                .addMetric("adjustedSalary", Metric.SUM, "totalAdjustedPayroll"));
    }

    @Test
    public void reportDefinitionsAndChartsShouldReuseRegisteredComputedFields() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .build();
        ReportDefinition<DepartmentAdjustedPayrollRow> report = PojoLens.report(
                PojoLens
                        .parse("select department, sum(adjustedSalary) as totalAdjustedPayroll "
                                + "group by department order by totalAdjustedPayroll desc")
                        .computedFields(registry),
                DepartmentAdjustedPayrollRow.class,
                ChartSpec.of(ChartType.BAR, "department", "totalAdjustedPayroll")
        );

        List<DepartmentAdjustedPayrollRow> rows = report.rows(sampleEmployees());
        ChartData chart = report.chart(sampleEmployees());

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertTrue(rows.get(0).totalAdjustedPayroll > rows.get(1).totalAdjustedPayroll);
        assertEquals(List.of("Engineering", "Finance"), chart.getLabels());
    }

    @Test
    public void dependentComputedFieldsShouldPreserveOrderingAndNumericCoercion() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("adjustedSalary", "salary * 1.1", Double.class)
                .add("roundedAdjustedSalary", "ROUND(adjustedSalary)", Integer.class)
                .build();

        List<RoundedAdjustedSalaryRow> rows = PojoLens.newQueryBuilder(sampleEmployees())
                .computedFields(registry)
                .addField("name")
                .addField("roundedAdjustedSalary")
                .initFilter()
                .filter(RoundedAdjustedSalaryRow.class);

        assertEquals(4, rows.size());
        Map<String, Integer> roundedByName = rows.stream()
                .collect(java.util.stream.Collectors.toMap(row -> row.name, row -> row.roundedAdjustedSalary));
        assertEquals(Integer.valueOf(132000), roundedByName.get("Alice"));
        assertEquals(Integer.valueOf(99000), roundedByName.get("Bob"));
        assertEquals(Integer.valueOf(143000), roundedByName.get("Cara"));
        assertEquals(Integer.valueOf(121000), roundedByName.get("Dan"));
    }

    public static class AdjustedSalaryRow {
        public String name;
        public double adjustedSalary;

        public AdjustedSalaryRow() {
        }
    }

    public static class DepartmentAdjustedPayrollRow {
        public String department;
        public double totalAdjustedPayroll;

        public DepartmentAdjustedPayrollRow() {
        }
    }

    public static class SalaryDeltaRow {
        public String name;
        public double salaryDelta;

        public SalaryDeltaRow() {
        }
    }

    public static class RoundedAdjustedSalaryRow {
        public String name;
        public int roundedAdjustedSalary;

        public RoundedAdjustedSalaryRow() {
        }
    }
}

