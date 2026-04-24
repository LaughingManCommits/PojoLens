package laughing.man.commits.report;

import laughing.man.commits.enums.Metric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportComparisonsTest {

    static class SaleRow {
        public double amount;

        SaleRow(double amount) {
            this.amount = amount;
        }
    }

    @Test
    void percentageDeltaPositive() {
        PeriodComparison c = ReportComparisons.of(110d, 100d);

        assertEquals("+10%", c.percentageDelta());
    }

    @Test
    void percentageDeltaNegative() {
        PeriodComparison c = ReportComparisons.of(90d, 100d);

        assertEquals("-10%", c.percentageDelta());
    }

    @Test
    void percentageDeltaFlat() {
        PeriodComparison c = ReportComparisons.of(0d, 0d);

        assertEquals("flat", c.percentageDelta());
    }

    @Test
    void percentageDeltaNew() {
        PeriodComparison c = ReportComparisons.of(50d, 0d);

        assertEquals("new", c.percentageDelta());
    }

    @Test
    void ratePointDeltaFormatted() {
        PeriodComparison c = ReportComparisons.of(0.92, 0.87);

        assertEquals("+5.0 pt", c.ratePointDelta());
    }

    @Test
    void compareCountUsesListSizes() {
        List<String> current = List.of("a", "b", "c");
        List<String> previous = List.of("a", "b");

        PeriodComparison c = ReportComparisons.compareCount(current, previous);

        assertEquals(3d, c.currentValue());
        assertEquals(2d, c.previousValue());
        assertEquals("+50%", c.percentageDelta());
    }

    @Test
    void compareSumAggregatesField() {
        List<SaleRow> current = List.of(new SaleRow(100), new SaleRow(200));
        List<SaleRow> previous = List.of(new SaleRow(150));

        PeriodComparison c = ReportComparisons.compare(current, previous, "amount", Metric.SUM);

        assertEquals(300d, c.currentValue());
        assertEquals(150d, c.previousValue());
    }

    @Test
    void compareAvgAggregatesField() {
        List<SaleRow> current = List.of(new SaleRow(100), new SaleRow(200));
        List<SaleRow> previous = List.of(new SaleRow(300));

        PeriodComparison c = ReportComparisons.compare(current, previous, "amount", Metric.AVG);

        assertEquals(150d, c.currentValue());
        assertEquals(300d, c.previousValue());
    }

    @Test
    void absoluteDeltaComputed() {
        PeriodComparison c = ReportComparisons.of(130d, 100d);

        assertEquals(30d, c.absoluteDelta(), 1e-9);
    }

    @Test
    void relativeDeltaNanWhenPreviousIsZero() {
        PeriodComparison c = ReportComparisons.of(50d, 0d);

        assertTrue(Double.isNaN(c.relativeDelta()));
    }
}
