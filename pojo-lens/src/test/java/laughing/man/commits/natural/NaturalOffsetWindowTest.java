package laughing.man.commits.natural;

import laughing.man.commits.PojoLensNatural;
import laughing.man.commits.domain.QueryRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-31: natural {@code previous}/{@code next} window phrases ({@code LAG}/{@code LEAD}).
 */
class NaturalOffsetWindowTest {

    @Test
    void previousAndNextPhrasesReadNeighbouringRows() {
        NaturalQuery query = PojoLensNatural.parse("show region, month, "
                + "previous amount by region ordered by month as prev amount, "
                + "next amount by region ordered by month for 2 rows defaulting to 0 as next amount "
                + "sort by region ascending, month ascending");

        List<SaleWindow> rows = query.filter(sales(), SaleWindow.class);

        assertEquals(Arrays.asList(null, 100, 150, null, 300), rows.stream().map(row -> row.prevAmount).toList());
        // east/1 reads east/3, whose amount is null: the default only applies outside the partition.
        assertEquals(Arrays.asList(null, 0, 0, 0, 0), rows.stream().map(row -> row.nextAmount).toList());
        assertTrue(query.equivalentSqlLike().contains("LAG(amount) OVER (PARTITION BY region ORDER BY month ASC)"),
                query::equivalentSqlLike);
        assertTrue(query.equivalentSqlLike().contains("LEAD(amount, 2, 0) OVER (PARTITION BY region ORDER BY month ASC)"),
                query::equivalentSqlLike);
    }

    @Test
    void qualifyFiltersOffsetWindowsByAliasOrInlinePhrase() {
        List<SaleWindow> byAlias = PojoLensNatural.parse("show region, month, "
                        + "previous month by region ordered by month as prev month "
                        + "qualify prev month is null sort by region ascending")
                .filter(sales(), SaleWindow.class);
        List<SaleWindow> inline = PojoLensNatural.parse("show region, month, "
                        + "next month by region ordered by month defaulting to 0 as next month "
                        + "qualify next month by region ordered by month defaulting to 0 is 0 sort by region ascending")
                .filter(sales(), SaleWindow.class);

        assertEquals(List.of("east/1", "west/1"), byAlias.stream().map(SaleWindow::key).toList());
        assertEquals(List.of("east/3", "west/2"), inline.stream().map(SaleWindow::key).toList());
    }

    @Test
    void windowPhrasesRunOverGroupedRows() {
        List<RegionRank> rows = PojoLensNatural.parse("show region, sum of amount as total, "
                        + "rank ordered by total descending as sales rank, "
                        + "previous total ordered by total descending as next higher total "
                        + "group by region qualify sales rank is at most 2 sort by sales rank ascending")
                .filter(sales(), RegionRank.class);

        assertEquals(List.of("west", "east"), rows.stream().map(row -> row.region).toList());
        assertEquals(List.of(550L, 250L), rows.stream().map(row -> row.total).toList());
        assertEquals(List.of(1L, 2L), rows.stream().map(row -> row.salesRank).toList());
        assertEquals(Arrays.asList(null, 550L), rows.stream().map(row -> row.nextHigherTotal).toList());
    }

    @Test
    void fieldsNamedPreviousOrNextStayPlainFields() {
        List<Named> rows = PojoLensNatural.parse("show previous, next where next is greater than 1")
                .filter(List.of(new Named(1, 1), new Named(2, 5)), Named.class);

        assertEquals(List.of(2), rows.stream().map(row -> row.previous).toList());
    }

    @Test
    void malformedOffsetPhrasesFailWithGuidance() {
        assertTrue(error("show previous amount ordered by month for -1 rows as p").contains("non-negative whole number"));
        assertTrue(error("show next amount ordered by month for last 2 rows as p").contains("non-negative whole number"));
        assertTrue(error("show previous amount ordered by month defaulting to as p").contains("defaulting to"));
        assertTrue(error("show previous amount ordered by month defaulting to :fallback as p")
                .contains("Parameters are not supported"));
        assertTrue(error("show previous amount ordered by month as").contains("alias"));
    }

    private static String error(String query) {
        return assertThrows(RuntimeException.class,
                () -> PojoLensNatural.parse(query).filter(sales(), QueryRow.class)).getMessage();
    }

    private static List<Sale> sales() {
        return List.of(
                new Sale("west", 2, 250),
                new Sale("east", 2, 150),
                new Sale("east", 1, 100),
                new Sale("west", 1, 300),
                new Sale("east", 3, null));
    }

    public static class Sale {
        public String region;
        public int month;
        public Integer amount;

        Sale() {
        }

        Sale(String region, int month, Integer amount) {
            this.region = region;
            this.month = month;
            this.amount = amount;
        }
    }

    public static class SaleWindow {
        public String region;
        public int month;
        public Integer prevAmount;
        public Integer nextAmount;
        public Integer prevMonth;
        public Integer nextMonth;

        SaleWindow() {
        }

        String key() {
            return region + "/" + month;
        }
    }

    public static class RegionRank {
        public String region;
        public Long total;
        public Long salesRank;
        public Long nextHigherTotal;

        RegionRank() {
        }
    }

    public static class Named {
        public int previous;
        public int next;

        Named() {
        }

        Named(int previous, int next) {
            this.previous = previous;
            this.next = next;
        }
    }
}
