package laughing.man.commits.dsl;

import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.internal.builder.QueryWindowFrame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-31: typed {@code lag}/{@code lead} and windows over grouped rows.
 */
class TypedWindowGapsTest {

    private static final TypedField<Sale, String> REGION = TypedField.of("region", String.class);
    private static final TypedField<Sale, String> REP = TypedField.of("rep", String.class);
    private static final TypedField<Sale, Integer> MONTH = TypedField.of("month", Integer.class);
    private static final TypedField<Sale, Integer> AMOUNT = TypedField.of("amount", Integer.class);
    private static final TypedField<RepTotal, Long> TOTAL = TypedField.of("total", Long.class);
    private static final TypedField<RepTotal, Long> R = TypedField.of("r", Long.class);

    @Test
    void lagAndLeadReadNeighbouringRowsWithOffsetsAndDefaults() {
        List<SaleWindow> rows = TypedQuery.from(Sale.class)
                .where(REGION.eq("east"))
                .window(WindowFunction.LAG, AMOUNT, "prevAmount", List.of(TypedWindowOrder.asc(MONTH)), REP)
                .lead(AMOUNT, "nextAmount", 1, -1, List.of(TypedWindowOrder.asc(MONTH)), REP)
                .orderBy(REP)
                .orderBy(MONTH)
                .filter(sales(), SaleWindow.class);

        // Eli: months 1 (50) and 3 (null); Eve: months 1 (100) and 2 (150).
        assertEquals(Arrays.asList(null, 50, null, 100), rows.stream().map(row -> row.prevAmount).toList());
        assertEquals(Arrays.asList(null, -1, 150, -1), rows.stream().map(row -> row.nextAmount).toList());
    }

    @Test
    void windowsRankAndQualifyGroupedRows() {
        List<RepTotal> rows = TypedQuery.from(Sale.class)
                .groupBy(REGION)
                .groupBy(REP)
                .metric(AMOUNT, Metric.SUM, TOTAL)
                .having(TOTAL.gt(60L))
                .window(WindowFunction.RANK, R, List.of(TypedWindowOrder.desc(TOTAL)))
                .lag(TOTAL, "prevTotal", 1, 0L, List.of(TypedWindowOrder.desc(TOTAL)), REGION)
                .qualify(R.lte(2L))
                .orderBy(R)
                .orderBy(REP)
                .filter(sales(), RepTotal.class);

        assertEquals(List.of("Val/320/1", "Eve/250/2", "Wu/250/2"), rows.stream().map(RepTotal::key).toList());
        assertEquals(List.of(0L, 0L, 320L), rows.stream().map(row -> row.prevTotal).toList());
    }

    @Test
    void planPreviewAndExplainDescribeOffsetWindows() {
        TypedQuery<Sale> query = TypedQuery.from(Sale.class)
                .lag(AMOUNT, "prevAmount", 2, 0, List.of(TypedWindowOrder.asc(MONTH)), REGION);

        TypedPlanWindow window = query.planPreview().windows().get(0);
        Object explained = query.explain(sales()).get("windows");

        assertEquals(WindowFunction.LAG, window.function());
        assertEquals("amount", window.valueField());
        assertEquals(2, window.offset());
        assertEquals(0, window.defaultValue());
        assertTrue(String.valueOf(explained).contains("prevAmount:LAG:value=amount"), String.valueOf(explained));
        assertTrue(String.valueOf(explained).contains("offset=2:default=0"), String.valueOf(explained));
    }

    @Test
    void offsetWindowsRejectBadArguments() {
        TypedQuery<Sale> query = TypedQuery.from(Sale.class);
        List<TypedWindowOrder> byMonth = List.of(TypedWindowOrder.asc(MONTH));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> query.lag(AMOUNT, "p", -1, null, byMonth)).getMessage().contains("lead(...)"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> query.window(WindowFunction.LEAD, AMOUNT, "p", QueryWindowFrame.fullPartition(), byMonth))
                .getMessage().contains("does not accept a window frame"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> query.groupBy(REGION).window(WindowFunction.RANK, "r", byMonth).filter(sales(), RepTotal.class))
                .getMessage().contains("require count/metric output"));
    }

    private static List<Sale> sales() {
        return List.of(
                new Sale("east", "Eve", 1, 100),
                new Sale("east", "Eve", 2, 150),
                new Sale("east", "Eli", 3, null),
                new Sale("east", "Eli", 1, 50),
                new Sale("west", "Wu", 2, 250),
                new Sale("west", "Val", 1, 300),
                new Sale("west", "Val", 2, 20));
    }

    public static class Sale {
        public String region;
        public String rep;
        public int month;
        public Integer amount;

        Sale() {
        }

        Sale(String region, String rep, int month, Integer amount) {
            this.region = region;
            this.rep = rep;
            this.month = month;
            this.amount = amount;
        }
    }

    public static class SaleWindow {
        public String region;
        public int month;
        public Integer prevAmount;
        public Integer nextAmount;

        SaleWindow() {
        }
    }

    public static class RepTotal {
        public String region;
        public String rep;
        public Long total;
        public Long r;
        public Long prevTotal;

        RepTotal() {
        }

        String key() {
            return rep + "/" + total + "/" + r;
        }
    }
}
