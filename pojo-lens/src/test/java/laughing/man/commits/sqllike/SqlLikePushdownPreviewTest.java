package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryStage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikePushdownPreviewTest {

    @Test
    void simpleFilterOrderAndLimitAreFullyPushable() {
        SqlLikePushdownPreview preview = PojoLensSql
                .parse("select name, salary where active = true order by salary desc limit 10")
                .pushdownPreview();

        assertEquals(SqlLikePushdownMode.FULL, preview.mode());
        assertTrue(preview.isFullyPushable());
        assertEquals(List.of("SELECT", "WHERE", "ORDER_BY", "LIMIT"), preview.pushableStages());
        assertTrue(preview.inMemoryStages().isEmpty());
        assertTrue(preview.fallbackReasons().isEmpty());
    }

    @Test
    void groupedQueryWithPushableWhereRequiresSplitExecution() {
        SqlLikePushdownPreview preview = PojoLensSql
                .parse("select department, count(*) as total where active = true "
                        + "group by department order by total desc")
                .pushdownPreview();

        assertEquals(SqlLikePushdownMode.SPLIT, preview.mode());
        assertTrue(preview.requiresSplitExecution());
        assertEquals(List.of("WHERE"), preview.pushableStages());
        assertTrue(preview.inMemoryStages().contains("GROUP_BY"));
        assertTrue(preview.inMemoryStages().contains("AGGREGATE"));
        assertTrue(preview.inMemoryStages().contains("ORDER_BY"));
        assertTrue(preview.fallbackReasons().contains("GROUPING_UNSUPPORTED"));
        assertTrue(preview.fallbackReasons().contains("AGGREGATION_UNSUPPORTED"));
    }

    @Test
    void joinOnlyQueryIsInMemoryOnly() {
        SqlLikePushdownPreview preview = PojoLensSql
                .parse("select * from companies join employees on id = companyId")
                .pushdownPreview();

        assertEquals(SqlLikePushdownMode.IN_MEMORY_ONLY, preview.mode());
        assertTrue(preview.isInMemoryOnly());
        assertTrue(preview.pushableStages().isEmpty());
        assertEquals(List.of("JOIN"), preview.inMemoryStages());
        assertEquals(List.of("JOIN_UNSUPPORTED"), preview.fallbackReasons());
    }

    @Test
    void unsupportedFilterOperatorKeepsWhereInMemory() {
        SqlLikePushdownPreview preview = PojoLensSql
                .parse("where name contains 'a'")
                .pushdownPreview();

        assertEquals(SqlLikePushdownMode.IN_MEMORY_ONLY, preview.mode());
        assertTrue(preview.inMemoryStages().contains("WHERE"));
        assertTrue(preview.fallbackReasons().contains("FILTER_OPERATOR_UNSUPPORTED"));
    }

    @Test
    void computedSelectCanSplitAfterPushableWhere() {
        SqlLikePushdownPreview preview = PojoLensSql
                .parse("select salary * 1.1 as adjusted where active = true")
                .pushdownPreview();

        assertEquals(SqlLikePushdownMode.SPLIT, preview.mode());
        assertEquals(List.of("WHERE"), preview.pushableStages());
        assertEquals(List.of("SELECT"), preview.inMemoryStages());
        assertEquals(List.of("COMPUTED_SELECT_UNSUPPORTED"), preview.fallbackReasons());
    }

    @Test
    void explainPayloadContainsPushdownPreview() {
        Map<String, Object> explain = PojoLensSql
                .parse("where active = true order by salary desc limit 2")
                .explain();

        @SuppressWarnings("unchecked")
        Map<String, Object> pushdown = (Map<String, Object>) explain.get("pushdownPreview");

        assertEquals("FULL", pushdown.get("mode"));
        assertEquals(Boolean.TRUE, pushdown.get("fullyPushable"));
        assertEquals(List.of("WHERE", "ORDER_BY", "LIMIT"), pushdown.get("pushableStages"));
    }

    @Test
    void bindTelemetryIncludesPushdownMetadata() {
        List<QueryTelemetryEvent> events = new ArrayList<>();

        PojoLensSql
                .parse("select department, count(*) as total where active = true group by department")
                .telemetry(events::add)
                .filter(sampleEmployees(), DepartmentTotal.class);

        QueryTelemetryEvent bind = events.stream()
                .filter(event -> event.stage() == QueryTelemetryStage.BIND)
                .findFirst()
                .orElseThrow();

        assertEquals("SPLIT", bind.metadata().get("pushdownMode"));
        assertEquals(List.of("WHERE"), bind.metadata().get("pushdownPushableStages"));
        assertFalse(((List<?>) bind.metadata().get("pushdownFallbackReasons")).isEmpty());
    }

    public static class DepartmentTotal {
        public String department;
        public long total;

        public DepartmentTotal() {
        }
    }
}
