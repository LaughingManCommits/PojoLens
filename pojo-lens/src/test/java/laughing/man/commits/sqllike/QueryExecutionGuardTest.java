package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.testutil.BusinessFixtures.Employee;

import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryExecutionGuardTest {

    // --- QueryExecutionGuard builder and basic accessors ---

    @Test
    void unrestrictedGuardShouldReportNoLimits() {
        QueryExecutionGuard guard = QueryExecutionGuard.unrestricted();

        assertTrue(guard.isUnrestricted());
        assertEquals(-1, guard.maxRowsScanned());
        assertEquals(-1, guard.maxRowsReturned());
        assertEquals(-1, guard.maxComplexityScore());
        assertEquals(-1L, guard.maxDurationMillis());
    }

    @Test
    void builderShouldSetAllLimits() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsScanned(1000)
                .maxRowsReturned(100)
                .maxComplexityScore(10)
                .maxDurationMillis(2000)
                .build();

        assertFalse(guard.isUnrestricted());
        assertEquals(1000, guard.maxRowsScanned());
        assertEquals(100, guard.maxRowsReturned());
        assertEquals(10, guard.maxComplexityScore());
        assertEquals(2000L, guard.maxDurationMillis());
    }

    @Test
    void builderShouldRejectNegativeLimits() {
        assertThrows(IllegalArgumentException.class, () -> QueryExecutionGuard.builder().maxRowsScanned(-1));
        assertThrows(IllegalArgumentException.class, () -> QueryExecutionGuard.builder().maxRowsReturned(-1));
        assertThrows(IllegalArgumentException.class, () -> QueryExecutionGuard.builder().maxComplexityScore(-1));
        assertThrows(IllegalArgumentException.class, () -> QueryExecutionGuard.builder().maxDurationMillis(-1));
    }

    // --- QueryGuardOutcome ---

    @Test
    void allowedOutcomeShouldHaveNullBlockFields() {
        QueryGuardOutcome outcome = QueryGuardOutcome.allowed(null);

        assertTrue(outcome.allowed());
        assertFalse(outcome.blocked());
        assertFalse(outcome.auditMetadata().containsKey("guardBlockCode"));
    }

    @Test
    void blockedOutcomeShouldCarryCodeAndReason() {
        QueryGuardOutcome outcome = QueryGuardOutcome.blocked(
                "GUARD_ROWS_SCANNED_EXCEEDED", "too many rows", null);

        assertFalse(outcome.allowed());
        assertTrue(outcome.blocked());
        assertEquals("GUARD_ROWS_SCANNED_EXCEEDED", outcome.blockCode());
        assertEquals("too many rows", outcome.blockReason());
    }

    @Test
    void auditMetadataShouldIncludeComplexitySummaryWhenPresent() {
        SqlLikePlanPreview preview = PojoLensSql.parse("where active = true").planPreview();
        QueryComplexitySummary summary = QueryComplexitySummary.from(preview);
        QueryGuardOutcome outcome = QueryGuardOutcome.blocked(
                "GUARD_COMPLEXITY_EXCEEDED", "too complex", summary);

        assertTrue(outcome.auditMetadata().containsKey("complexityScore"));
        assertTrue(outcome.auditMetadata().containsKey("filterCount"));
    }

    // --- QueryComplexitySummary scoring ---

    @Test
    void complexitySummaryShouldScoreFilters() {
        SqlLikePlanPreview preview = PojoLensSql
                .parse("where active = true and salary > 50000")
                .planPreview();

        QueryComplexitySummary summary = QueryComplexitySummary.from(preview);

        assertEquals(2, summary.filterCount());
        assertEquals(2, summary.estimatedComplexityScore());
    }

    @Test
    void complexitySummaryShouldScoreGrouping() {
        SqlLikePlanPreview preview = PojoLensSql
                .parse("select department group by department")
                .planPreview();

        QueryComplexitySummary summary = QueryComplexitySummary.from(preview);

        assertTrue(summary.hasGrouping());
        assertEquals(2, summary.estimatedComplexityScore());
    }

    // --- SqlLikeQuery guard integration: pre-execution ---

    @Test
    void unrestrictedGuardShouldNotBlockExecution() {
        List<Employee> result = PojoLensSql.parse("where active = true")
                .executionGuard(QueryExecutionGuard.unrestricted())
                .filter(sampleEmployees(), Employee.class);

        assertEquals(3, result.size());
    }

    @Test
    void guardShouldBlockWhenRowsScannedExceedsLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsScanned(2)
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true")
                        .executionGuard(guard)
                        .filter(sampleEmployees(), Employee.class));

        assertEquals("GUARD_ROWS_SCANNED_EXCEEDED", ex.outcome().blockCode());
        assertNotNull(ex.outcome().blockReason());
    }

    @Test
    void guardShouldAllowWhenRowsScannedWithinLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsScanned(10)
                .build();

        List<Employee> result = PojoLensSql.parse("where active = true")
                .executionGuard(guard)
                .filter(sampleEmployees(), Employee.class);

        assertEquals(3, result.size());
    }

    @Test
    void guardShouldBlockWhenComplexityExceedsLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxComplexityScore(0)
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true and salary > 50000")
                        .executionGuard(guard)
                        .filter(sampleEmployees(), Employee.class));

        assertEquals("GUARD_COMPLEXITY_EXCEEDED", ex.outcome().blockCode());
    }

    // --- SqlLikeQuery guard integration: post-execution ---

    @Test
    void guardShouldBlockWhenRowsReturnedExceedsLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsReturned(1)
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true")
                        .executionGuard(guard)
                        .filter(sampleEmployees(), Employee.class));

        assertEquals("GUARD_ROWS_RETURNED_EXCEEDED", ex.outcome().blockCode());
    }

    @Test
    void guardShouldAllowWhenRowsReturnedWithinLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsReturned(10)
                .build();

        List<Employee> result = PojoLensSql.parse("where active = true")
                .executionGuard(guard)
                .filter(sampleEmployees(), Employee.class);

        assertEquals(3, result.size());
    }

    // --- QueryExecutionGuardException ---

    @Test
    void guardExceptionShouldExposeOutcome() {
        QueryGuardOutcome outcome = QueryGuardOutcome.blocked("GUARD_ROWS_SCANNED_EXCEEDED", "test", null);
        QueryExecutionGuardException ex = new QueryExecutionGuardException(outcome);

        assertEquals(outcome, ex.outcome());
        assertEquals("test", ex.getMessage());
    }

    // --- Guard copy-with (SqlLikeQuery fluent API) ---

    @Test
    void executionGuardShouldReturnSameInstanceWhenUnchanged() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsScanned(10).build();
        SqlLikeQuery query = PojoLensSql.parse("where active = true").executionGuard(guard);

        assertTrue(query.executionGuard() == query.executionGuard(guard).executionGuard());
    }

    // --- NaturalQuery guard integration ---

    @Test
    void naturalQueryGuardShouldBlockWhenRowsScannedExceedsLimit() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsScanned(2)
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> NaturalQuery.of("show employees where active is true")
                        .executionGuard(guard)
                        .filter(sampleEmployees(), Employee.class));

        assertEquals("GUARD_ROWS_SCANNED_EXCEEDED", ex.outcome().blockCode());
    }

    @Test
    void naturalQueryGuardShouldPassThroughWhenWithinLimits() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsScanned(100)
                .maxRowsReturned(100)
                .build();

        List<Employee> result = NaturalQuery.of("show employees where active is true")
                .executionGuard(guard)
                .filter(sampleEmployees(), Employee.class);

        assertFalse(result.isEmpty());
    }

    // --- Guard outcome: pre-execution check internals ---

    @Test
    void checkPreExecutionShouldBlockOnRowsScanned() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsScanned(5).build();
        SqlLikePlanPreview preview = PojoLensSql.parse("where active = true").planPreview();

        QueryGuardOutcome outcome = guard.checkPreExecution(preview, 10);

        assertTrue(outcome.blocked());
        assertEquals("GUARD_ROWS_SCANNED_EXCEEDED", outcome.blockCode());
    }

    @Test
    void checkPreExecutionShouldAllowWhenWithinLimits() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsScanned(100).build();
        SqlLikePlanPreview preview = PojoLensSql.parse("where active = true").planPreview();

        QueryGuardOutcome outcome = guard.checkPreExecution(preview, 5);

        assertTrue(outcome.allowed());
    }

    @Test
    void checkPostExecutionShouldBlockOnRowsReturned() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsReturned(2).build();

        QueryGuardOutcome outcome = guard.checkPostExecution(5, 100L);

        assertTrue(outcome.blocked());
        assertEquals("GUARD_ROWS_RETURNED_EXCEEDED", outcome.blockCode());
    }

    @Test
    void checkPostExecutionShouldBlockOnDuration() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxDurationMillis(100).build();

        QueryGuardOutcome outcome = guard.checkPostExecution(5, 200L);

        assertTrue(outcome.blocked());
        assertEquals("GUARD_DURATION_EXCEEDED", outcome.blockCode());
    }

    @Test
    void checkPostExecutionShouldAllowWhenWithinLimits() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsReturned(100)
                .maxDurationMillis(5000)
                .build();

        QueryGuardOutcome outcome = guard.checkPostExecution(10, 50L);

        assertTrue(outcome.allowed());
    }
}
