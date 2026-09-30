package laughing.man.commits;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.sqllike.PlanPreviewField;
import laughing.man.commits.table.TabularSchema;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-31 slice 1: {@code LAG}/{@code LEAD} window functions in SQL-like queries.
 */
class WindowOffsetQueryTest {

    @Test
    void lagAndLeadReadNeighbouringRowsWithinEachPartition() {
        List<SaleWindow> rows = PojoLensSql.parse("select region, month, amount, "
                        + "lag(amount) over (partition by region order by month) as prevAmount, "
                        + "lead(amount) over (partition by region order by month) as nextAmount "
                        + "order by region, month")
                .filter(sales(), SaleWindow.class);

        assertEquals(List.of("east/1", "east/2", "east/3", "west/1", "west/2"),
                rows.stream().map(SaleWindow::key).toList());
        assertEquals(Arrays.asList(null, 100, 150), rows.subList(0, 3).stream().map(row -> row.prevAmount).toList());
        assertEquals(Arrays.asList(150, null, null), rows.subList(0, 3).stream().map(row -> row.nextAmount).toList());
        assertEquals(Arrays.asList(null, 300), rows.subList(3, 5).stream().map(row -> row.prevAmount).toList());
        assertEquals(Arrays.asList(250, null), rows.subList(3, 5).stream().map(row -> row.nextAmount).toList());
    }

    @Test
    void offsetAndDefaultApplyOnlyOutsideThePartition() {
        List<SaleWindow> rows = PojoLensSql.parse("select region, month, amount, "
                        + "lag(amount, 2, 0) over (partition by region order by month) as prevAmount, "
                        + "lead(amount, 1, -1) over (partition by region order by month) as nextAmount "
                        + "where region = 'east' order by month")
                .filter(sales(), SaleWindow.class);

        // east/3 has a null amount: LEAD from east/2 reads that null, not the default.
        assertEquals(Arrays.asList(0, 0, 100), rows.stream().map(row -> row.prevAmount).toList());
        assertEquals(Arrays.asList(150, null, -1), rows.stream().map(row -> row.nextAmount).toList());
    }

    @Test
    void zeroOffsetReadsTheCurrentRow() {
        List<SaleWindow> rows = PojoLensSql.parse("select region, month, amount, "
                        + "lag(amount, 0) over (order by region, month) as prevAmount order by region, month")
                .filter(sales(), SaleWindow.class);

        rows.forEach(row -> assertEquals(row.amount, row.prevAmount));
    }

    @Test
    void textValuesAndTextDefaultsKeepTheirType() {
        List<SaleWindow> rows = PojoLensSql.parse("select region, month, "
                        + "lag(rep, 1, 'none') over (partition by region order by month desc) as prevRep "
                        + "where region = 'west' order by month")
                .filter(sales(), SaleWindow.class);
        TabularSchema schema = PojoLensSql.parse("select lag(rep) over (order by month) as prevRep, "
                + "lead(amount) over (order by month) as nextAmount").schema(Sale.class);

        assertEquals(List.of("Wu", "none"), rows.stream().map(row -> row.prevRep).toList());
        assertEquals(String.class, schema.column("prevRep").type());
        assertEquals(Integer.class, schema.column("nextAmount").type());
    }

    @Test
    void qualifyAndOrderByUseOffsetWindowAliases() {
        List<SaleWindow> firstMonths = PojoLensSql.parse("select region, month, "
                        + "lag(month) over (partition by region order by month) as prevMonth "
                        + "qualify prevMonth is null order by region")
                .filter(sales(), SaleWindow.class);
        List<SaleWindow> byPrevious = PojoLensSql.parse("select region, month, amount, "
                        + "lag(amount, 1, 0) over (partition by region order by month) as prevAmount "
                        + "qualify prevAmount >= 100 order by prevAmount desc")
                .filter(sales(), SaleWindow.class);

        assertEquals(List.of("east/1", "west/1"), firstMonths.stream().map(SaleWindow::key).toList());
        assertEquals(List.of("west/2", "east/3", "east/2"), byPrevious.stream().map(SaleWindow::key).toList());
    }

    @Test
    void offsetArgumentsSurviveJoinCanonicalization() {
        JoinBindings bindings = JoinBindings.of("targets", List.of(new Target("east", 120), new Target("west", 200)));

        List<SaleWindow> rows = PojoLensSql.parse("select region, month, amount, "
                        + "lag(amount, 1, 7) over (partition by region order by month) as prevAmount "
                        + "from sales join targets on region = targetRegion order by region, month")
                .filter(sales(), bindings, SaleWindow.class);

        assertEquals(Arrays.asList(7, 100, 150, 7, 300), rows.stream().map(row -> row.prevAmount).toList());
    }

    @Test
    void planPreviewDescribesOffsetWindowsWithoutAFrame() {
        PlanPreviewField field = PojoLensSql
                .parse("select lag(amount, 2, 0) over (partition by region order by month) as prevAmount")
                .planPreview()
                .selectFields()
                .get(0);

        assertEquals("LAG", field.windowFunction().toUpperCase());
        assertNull(field.windowFrame());
        assertEquals(List.of("region"), field.windowPartitionFields());
        assertTrue(field.field().toLowerCase().startsWith("lag(amount,2,0)over"), field::field);
    }

    @Test
    void functionNamesStayUsableAsFieldNames() {
        List<Named> rows = List.of(new Named("a", 1, 2), new Named("b", 3, 4));

        List<Named> filtered = PojoLensSql.parse("select name, lag, lead where lag > 1").filter(rows, Named.class);

        assertEquals(List.of("b"), filtered.stream().map(row -> row.name).toList());
    }

    @Test
    void parserRejectsMalformedOffsetArguments() {
        assertTrue(error("select lag(amount, -1) over (order by month) as p").contains("use LEAD"));
        assertTrue(error("select lead(amount, 1.5) over (order by month) as p").contains("non-negative integer"));
        assertTrue(error("select lag(amount, 1, region) over (order by month) as p").contains("must be a literal"));
        assertTrue(error("select lag(*) over (order by month) as p").contains("Expected field inside LAG"));
        assertTrue(error("select lag(amount) over (order by month rows between unbounded preceding and current row) as p")
                .contains("LAG does not accept a window frame"));
        assertTrue(error("select lag(amount) over (order by month)").contains("require AS alias"));
    }

    @Test
    void validationRejectsDefaultsThatDoNotFitTheValueField() {
        assertTrue(error("select lag(amount, 1, 0.5) over (order by month) as p").contains("does not fit Integer"));
        assertTrue(error("select lag(amount, 1, 'zero') over (order by month) as p").contains("does not match Integer"));
        assertTrue(error("select lag(rep, 1, 3) over (order by month) as p").contains("does not match String"));
        assertTrue(error("select lag(missing) over (order by month) as p").contains("Unknown field 'missing'"));
        assertTrue(error("select lead(amount) over (partition by region) as p").contains("ORDER BY"));
    }

    private static String error(String query) {
        return assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse(query).filter(sales(), QueryRow.class)).getMessage();
    }

    private static List<Sale> sales() {
        return List.of(
                new Sale("west", 2, 250, "Wu"),
                new Sale("east", 2, 150, "Eve"),
                new Sale("east", 1, 100, "Eve"),
                new Sale("west", 1, 300, "Val"),
                new Sale("east", 3, null, "Eli"));
    }

    public static class Sale {
        public String region;
        public int month;
        public Integer amount;
        public String rep;

        Sale() {
        }

        Sale(String region, int month, Integer amount, String rep) {
            this.region = region;
            this.month = month;
            this.amount = amount;
            this.rep = rep;
        }
    }

    public static class Target {
        public String targetRegion;
        public int target;

        Target() {
        }

        Target(String targetRegion, int target) {
            this.targetRegion = targetRegion;
            this.target = target;
        }
    }

    public static class Named {
        public String name;
        public int lag;
        public int lead;

        Named() {
        }

        Named(String name, int lag, int lead) {
            this.name = name;
            this.lag = lag;
            this.lead = lead;
        }
    }

    public static class SaleWindow {
        public String region;
        public int month;
        public Integer amount;
        public Integer prevAmount;
        public Integer nextAmount;
        public Integer prevMonth;
        public String prevRep;

        SaleWindow() {
        }

        String key() {
            return region + "/" + month;
        }
    }
}
