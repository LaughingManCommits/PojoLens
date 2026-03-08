package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.snapshot.SnapshotComparison;
import laughing.man.commits.snapshot.SnapshotComparisonSummary;
import laughing.man.commits.snapshot.SnapshotDeltaRow;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SnapshotComparisonTest {

    private static final long FIXED_HIRE_DATE_MILLIS = 1735689600000L;

    @Test
    public void snapshotComparisonShouldClassifyAddedRemovedChangedAndUnchangedRows() {
        SnapshotComparison<Employee, Integer> comparison = PojoLens.compareSnapshots(currentEmployees(), previousEmployees())
                .byKey(employee -> employee.id);

        SnapshotComparisonSummary summary = comparison.summary();

        assertEquals(4, summary.currentCount());
        assertEquals(4, summary.previousCount());
        assertEquals(1, summary.addedCount());
        assertEquals(1, summary.removedCount());
        assertEquals(2, summary.changedCount());
        assertEquals(1, summary.unchangedCount());

        SnapshotDeltaRow<Employee, Integer> changed = comparison.changed().stream()
                .filter(row -> Integer.valueOf(1).equals(row.key))
                .findFirst()
                .orElseThrow();
        assertEquals("CHANGED", changed.changeType);
        assertEquals(2, changed.changedFieldCount);
        assertTrue(changed.changedFieldSummary.contains("salary"));
        assertTrue(changed.changedFieldSummary.contains("active"));
    }

    @Test
    public void snapshotDeltaRowsShouldBeQueryableViaSqlLikeAndChartFlows() {
        SnapshotComparison<Employee, Integer> comparison = PojoLens.compareSnapshots(currentEmployees(), previousEmployees())
                .byKey(employee -> employee.id);

        List<ChangeProjection> changedRows = PojoLens
                .parse("select keyText, changedFieldCount, changedFieldSummary "
                        + "where changeType = 'CHANGED' order by keyText asc")
                .filter(comparison.rows(), ChangeProjection.class);

        ReportDefinition<ChangeCountRow> report = PojoLens.report(
                PojoLens.parse("select changeType, count(*) as total group by changeType order by changeType asc"),
                ChangeCountRow.class,
                ChartSpec.of(ChartType.BAR, "changeType", "total")
        );
        List<ChangeCountRow> summaryRows = report.rows(comparison.rows());
        ChartData chart = report.chart(comparison.rows());

        assertEquals(2, changedRows.size());
        assertEquals("1", changedRows.get(0).keyText);
        assertEquals(2, changedRows.get(0).changedFieldCount);
        assertTrue(changedRows.get(0).changedFieldSummary.contains("salary"));
        assertEquals(4, summaryRows.size());
        assertEquals(List.of("ADDED", "CHANGED", "REMOVED", "UNCHANGED"), chart.getLabels());
    }

    @Test
    public void snapshotComparisonShouldRejectNullAndDuplicateKeysByDefault() {
        try {
            PojoLens.compareSnapshots(
                            List.of(new Employee(1, "A", "X", 10, new Date(), true), new Employee(1, "B", "X", 20, new Date(), true)),
                            List.of()
                    )
                    .byKey(employee -> employee.id);
            fail("Expected duplicate key failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Duplicate snapshot key"));
        }

        try {
            PojoLens.compareSnapshots(
                            List.of(new Employee(1, "A", "X", 10, new Date(), true)),
                            List.of(new Employee(2, "B", "X", 20, new Date(), true))
                    )
                    .byKey(employee -> employee.department == null ? null : null);
            fail("Expected null key failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("null key"));
        }
    }

    @Test
    public void snapshotComparisonShouldAllowNullKeysWhenConfigured() {
        NullKeyRow current = new NullKeyRow(null, "current");
        NullKeyRow previous = new NullKeyRow(null, "previous");

        SnapshotComparison<NullKeyRow, String> comparison = PojoLens
                .compareSnapshots(List.of(current), List.of(previous))
                .allowNullKeys()
                .byKey(row -> row.externalId);

        assertEquals(1, comparison.changed().size());
        assertEquals(0, comparison.summary().addedCount());
    }

    private static List<Employee> previousEmployees() {
        Date now = new Date(FIXED_HIRE_DATE_MILLIS);
        return List.of(
                new Employee(1, "Alice", "Engineering", 100000, now, false),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 125000, now, true),
                new Employee(5, "Eve", "Support", 70000, now, true)
        );
    }

    private static List<Employee> currentEmployees() {
        Date now = new Date(FIXED_HIRE_DATE_MILLIS);
        return List.of(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, false)
        );
    }

    public static class ChangeProjection {
        public String keyText;
        public int changedFieldCount;
        public String changedFieldSummary;

        public ChangeProjection() {
        }
    }

    public static class ChangeCountRow {
        public String changeType;
        public long total;

        public ChangeCountRow() {
        }
    }

    public static class NullKeyRow {
        public String externalId;
        public String value;

        public NullKeyRow() {
        }

        public NullKeyRow(String externalId, String value) {
            this.externalId = externalId;
            this.value = value;
        }
    }
}

