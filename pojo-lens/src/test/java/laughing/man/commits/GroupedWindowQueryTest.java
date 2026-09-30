package laughing.man.commits;

import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.sqllike.PageResult;
import laughing.man.commits.table.TabularSchema;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-31 slice 2: window functions and {@code QUALIFY} over grouped rows in SQL-like queries.
 */
class GroupedWindowQueryTest {

    @Test
    void rankOrdersGroupsByAnAggregateAlias() {
        List<RegionTotal> rows = PojoLensSql.parse("select region, sum(amount) as total, "
                        + "rank() over (order by total desc) as r group by region order by r")
                .filter(sales(), RegionTotal.class);

        assertEquals(List.of("west", "east"), rows.stream().map(row -> row.region).toList());
        assertEquals(List.of(570L, 300L), rows.stream().map(row -> row.total).toList());
        assertEquals(List.of(1L, 2L), rows.stream().map(row -> row.r).toList());
    }

    @Test
    void runningTotalsAndOffsetsReadGroupedRowsInWindowOrder() {
        List<MonthTotal> rows = PojoLensSql.parse("select month, sum(amount) as total, "
                        + "sum(total) over (order by month rows between unbounded preceding and current row) as running, "
                        + "lag(total) over (order by month) as prevTotal, "
                        + "lead(total, 1, 0) over (order by month) as nextTotal, "
                        + "count(*) over (order by month rows between unbounded preceding and unbounded following) as months "
                        + "group by month order by month")
                .filter(sales(), MonthTotal.class);

        // Month 3 only has a null amount, so its SUM is null and the running total holds.
        assertEquals(Arrays.asList(450L, 420L, null), rows.stream().map(row -> row.total).toList());
        assertEquals(List.of(450L, 870L, 870L), rows.stream().map(row -> row.running).toList());
        assertEquals(Arrays.asList(null, 450L, 420L), rows.stream().map(row -> row.prevTotal).toList());
        assertEquals(Arrays.asList(420L, null, 0L), rows.stream().map(row -> row.nextTotal).toList());
        assertEquals(List.of(3L, 3L, 3L), rows.stream().map(row -> row.months).toList());
    }

    @Test
    void qualifyKeepsTheTopGroupPerPartition() {
        List<RepTotal> rows = PojoLensSql.parse("select region, rep, sum(amount) as total, "
                        + "row_number() over (partition by region order by total desc) as rn "
                        + "group by region, rep qualify rn = 1 order by region")
                .filter(sales(), RepTotal.class);

        assertEquals(List.of("east/Eve/250", "west/Val/320"), rows.stream().map(RepTotal::key).toList());
    }

    @Test
    void havingRunsBeforeWindowsAndQualifyAfter() {
        List<RepTotal> rows = PojoLensSql.parse("select region, rep, sum(amount) as total, "
                        + "rank() over (order by total desc) as r "
                        + "group by region, rep having total > 100 qualify r <= 2 order by r, rep")
                .filter(sales(), RepTotal.class);

        // Eli (50) is dropped by HAVING before ranking; Eve and Wu tie at 250.
        assertEquals(List.of("west/Val/320", "east/Eve/250", "west/Wu/250"), rows.stream().map(RepTotal::key).toList());
        assertEquals(List.of(1L, 2L, 2L), rows.stream().map(row -> row.r).toList());
    }

    @Test
    void windowsMayReferenceGroupBySelectAliases() {
        List<AreaRank> rows = PojoLensSql.parse("select region as area, rep, sum(amount) as total, "
                        + "dense_rank() over (partition by area order by total desc) as r "
                        + "group by area, rep order by area, r")
                .filter(sales(), AreaRank.class);

        assertEquals(List.of("east/Eve/1", "east/Eli/2", "west/Val/1", "west/Wu/2"),
                rows.stream().map(AreaRank::key).toList());
    }

    @Test
    void distinctAndPagingApplyAfterGroupedWindows() {
        List<RepTotal> page = PojoLensSql.parse("select region, rep, sum(amount) as total, "
                        + "rank() over (order by total desc) as r group by region, rep order by r, rep limit 2 offset 1")
                .filter(sales(), RepTotal.class);

        assertEquals(List.of("east/Eve/250", "west/Wu/250"), page.stream().map(RepTotal::key).toList());
    }

    @Test
    void keysetPagesOverGroupedWindowAliases() {
        String query = "select region, rep, sum(amount) as total, "
                + "row_number() over (order by total desc) as r group by region, rep order by r limit 2";

        PageResult<RepTotal> first = PojoLensSql.parse(query).filterPage(sales(), RepTotal.class);
        PageResult<RepTotal> second = PojoLensSql.parse(query)
                .keysetAfter(first.nextCursor().orElseThrow()).filterPage(sales(), RepTotal.class);

        assertEquals(List.of(1L, 2L), first.rows().stream().map(row -> row.r).toList());
        assertEquals(List.of(3L, 4L), second.rows().stream().map(row -> row.r).toList());
        assertEquals("east/Eli/50", second.rows().get(1).key());
    }

    @Test
    void explainCountsHavingAndQualifySeparatelyOnGroupedRows() {
        Object counts = PojoLensSql.parse("select region, rep, sum(amount) as total, "
                        + "row_number() over (partition by region order by total desc) as rn "
                        + "group by region, rep having total > 60 qualify rn = 1")
                .explain(sales(), QueryRow.class)
                .get("stageRowCounts");
        Map<?, ?> stages = (Map<?, ?>) counts;

        assertEquals(Map.of("applied", true, "before", 7, "after", 4), stages.get("group"));
        assertEquals(Map.of("applied", true, "before", 4, "after", 3), stages.get("having"));
        assertEquals(Map.of("applied", true, "before", 3, "after", 2), stages.get("qualify"));
    }

    @Test
    void schemaTypesGroupedWindowOutputs() {
        TabularSchema schema = PojoLensSql.parse("select month, sum(amount) as total, "
                        + "lag(total) over (order by month) as prevTotal, "
                        + "rank() over (order by total desc) as r group by month")
                .schema(QueryRow.class);

        assertEquals(Long.class, schema.column("r").type());
        assertTrue(schema.column("prevTotal").formatHint().contains("LAG"), schema.column("prevTotal")::formatHint);
    }

    @Test
    void groupedWindowsRejectReferencesOutsideTheGroupedRows() {
        assertTrue(error("select region, sum(amount) as total, rank() over (order by amount desc) as r group by region")
                .contains("Unknown field 'amount'"));
        assertTrue(error("select region, sum(amount) as total, rank() over (partition by rep order by total) as r "
                + "group by region").contains("Unknown field 'rep'"));
        assertTrue(error("select region, sum(amount) as total, sum(region) over (order by total "
                + "rows between unbounded preceding and current row) as s group by region")
                .contains("requires numeric field 'region'"));
        assertTrue(error("select region, sum(amount) as total, rank() over (order by sum(amount) desc) as r group by region")
                .contains("reference the alias instead"));
    }

    @Test
    void qualifyOnGroupedQueriesStillFiltersWindowOutputsOnly() {
        assertTrue(error("select region, sum(amount) as total, rank() over (order by total desc) as r "
                + "group by region qualify total > 1").contains("Unknown field 'total'"));
        assertTrue(error("select region, sum(amount) as total group by region qualify total > 1")
                .contains("QUALIFY requires at least one window SELECT output"));
    }

    private static String error(String query) {
        return assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse(query).filter(sales(), QueryRow.class)).getMessage();
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

    public static class RegionTotal {
        public String region;
        public Long total;
        public Long r;

        RegionTotal() {
        }
    }

    public static class MonthTotal {
        public int month;
        public Long total;
        public Long running;
        public Long prevTotal;
        public Long nextTotal;
        public Long months;

        MonthTotal() {
        }
    }

    public static class RepTotal {
        public String region;
        public String rep;
        public Long total;
        public Long rn;
        public Long r;

        RepTotal() {
        }

        String key() {
            return region + "/" + rep + "/" + total;
        }
    }

    public static class AreaRank {
        public String area;
        public String rep;
        public Long total;
        public Long r;

        AreaRank() {
        }

        String key() {
            return area + "/" + rep + "/" + r;
        }
    }
}
