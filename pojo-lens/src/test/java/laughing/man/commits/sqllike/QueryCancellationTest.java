package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.testutil.BusinessFixtures.Employee;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryCancellationTest {

    // fixtures: Alice(Eng,120k,active), Bob(Fin,90k,active), Cara(Eng,130k,active), Dan(Eng,110k,inactive)

    // --- QueryCancellationToken contract ---

    @Test
    void cancellationTokenShouldMirrorAtomicBoolean() {
        AtomicBoolean flag = new AtomicBoolean(false);
        QueryCancellationToken token = QueryCancellationToken.ofAtomic(flag);

        assertFalse(token.isCancelled());

        flag.set(true);
        assertTrue(token.isCancelled());
    }

    @Test
    void cancellationTokenOfAtomicShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> QueryCancellationToken.ofAtomic(null));
    }

    @Test
    void cancellationTokenShouldReflectThreadInterruptStatus() throws Exception {
        Thread testThread = Thread.currentThread();
        QueryCancellationToken token = QueryCancellationToken.ofThread(testThread);

        assertFalse(token.isCancelled());

        testThread.interrupt();
        try {
            assertTrue(token.isCancelled());
        } finally {
            Thread.interrupted(); // clear interrupt flag
        }
    }

    @Test
    void cancellationTokenShouldReflectVirtualThreadInterruptStatus() throws Exception {
        AtomicBoolean beforeInterrupt = new AtomicBoolean(true);
        AtomicBoolean afterInterrupt = new AtomicBoolean(false);
        CountDownLatch ready = new CountDownLatch(1);

        Thread virtualThread = Thread.ofVirtual().start(() -> {
            QueryCancellationToken token = QueryCancellationToken.ofThread(Thread.currentThread());
            beforeInterrupt.set(token.isCancelled());
            ready.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                Thread.onSpinWait();
            }
            afterInterrupt.set(token.isCancelled());
            Thread.interrupted();
        });

        ready.await();
        virtualThread.interrupt();
        virtualThread.join();

        assertFalse(beforeInterrupt.get());
        assertTrue(afterInterrupt.get());
    }

    @Test
    void cancellationTokenOfThreadShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> QueryCancellationToken.ofThread(null));
    }

    // --- QueryGuardOutcome.cancelled() ---

    @Test
    void cancelledOutcomeShouldCarryBlockCodeAndAbortMetadata() {
        QueryGuardOutcome outcome = QueryGuardOutcome.cancelled("GUARD_CANCELLED", "cancelled by caller", 3, null);

        assertFalse(outcome.allowed());
        assertTrue(outcome.blocked());
        assertEquals("GUARD_CANCELLED", outcome.blockCode());
        assertEquals("cancelled by caller", outcome.blockReason());
        assertEquals(3, outcome.rowsReturnedBeforeAbort());
        assertNull(outcome.complexitySummary());
    }

    @Test
    void cancelledOutcomeShouldIncludeAbortMetadataInAuditMap() {
        QueryGuardOutcome outcome = QueryGuardOutcome.cancelled("GUARD_CANCELLED", "reason", 5, null);

        assertEquals(Boolean.FALSE, outcome.auditMetadata().get("guardAllowed"));
        assertEquals("GUARD_CANCELLED", outcome.auditMetadata().get("guardBlockCode"));
        assertEquals(5, outcome.auditMetadata().get("rowsReturnedBeforeAbort"));
    }

    @Test
    void cancelledOutcomeShouldRejectNegativeRowCount() {
        assertThrows(IllegalArgumentException.class,
                () -> QueryGuardOutcome.cancelled("GUARD_CANCELLED", "reason", -1, null));
    }

    @Test
    void blockedOutcomeShouldNotPopulateRowsReturnedBeforeAbort() {
        QueryGuardOutcome outcome = QueryGuardOutcome.blocked("GUARD_ROWS_SCANNED_EXCEEDED", "too many", null);

        assertNull(outcome.rowsReturnedBeforeAbort());
        assertFalse(outcome.auditMetadata().containsKey("rowsReturnedBeforeAbort"));
    }

    // --- QueryExecutionGuard.checkCancellation() ---

    @Test
    void checkCancellationShouldReturnAllowedWhenNoToken() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder().maxRowsScanned(100).build();

        QueryGuardOutcome outcome = guard.checkCancellation(5);

        assertTrue(outcome.allowed());
    }

    @Test
    void checkCancellationShouldReturnAllowedWhenTokenNotFired() {
        AtomicBoolean flag = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(flag))
                .build();

        QueryGuardOutcome outcome = guard.checkCancellation(5);

        assertTrue(outcome.allowed());
    }

    @Test
    void checkCancellationShouldReturnCancelledWhenTokenFired() {
        AtomicBoolean flag = new AtomicBoolean(true);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(flag))
                .build();

        QueryGuardOutcome outcome = guard.checkCancellation(7);

        assertTrue(outcome.blocked());
        assertEquals("GUARD_CANCELLED", outcome.blockCode());
        assertEquals(7, outcome.rowsReturnedBeforeAbort());
    }

    @Test
    void checkCancellationShouldRejectNegativeRowsReturned() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(() -> false)
                .build();

        assertThrows(IllegalArgumentException.class, () -> guard.checkCancellation(-1));
    }

    // --- isUnrestricted with cancellation token ---

    @Test
    void guardWithOnlyCancellationTokenShouldNotBeUnrestricted() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(() -> false)
                .build();

        assertFalse(guard.isUnrestricted());
    }

    @Test
    void unrestrictedGuardShouldHaveNullToken() {
        assertNull(QueryExecutionGuard.unrestricted().cancellationToken());
    }

    @Test
    void guardBuilderShouldRejectNullToken() {
        assertThrows(NullPointerException.class,
                () -> QueryExecutionGuard.builder().cancellationToken(null));
    }

    // --- SqlLikeQuery: pre-execution cancellation ---

    @Test
    void preExecutionCancelledTokenShouldBlockFilterImmediately() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true")
                        .executionGuard(guard)
                        .filter(sampleEmployees(), Employee.class));

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(0, ex.outcome().rowsReturnedBeforeAbort());
    }

    @Test
    void preExecutionCancelledTokenShouldBlockStreamImmediately() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true")
                        .executionGuard(guard)
                        .stream(sampleEmployees(), Employee.class)
                        .toList());

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(0, ex.outcome().rowsReturnedBeforeAbort());
    }

    @Test
    void nonFiredTokenShouldAllowExecution() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();

        List<Employee> result = PojoLensSql.parse("where active = true")
                .executionGuard(guard)
                .filter(sampleEmployees(), Employee.class);

        assertEquals(3, result.size());
    }

    // --- SqlLikeQuery: mid-stream cancellation with deterministic abort metadata ---

    @Test
    void midStreamCancellationShouldCarryExactRowsReturnedBeforeAbort() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        List<Employee> received = new ArrayList<>();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true")
                        .executionGuard(guard)
                        .stream(sampleEmployees(), Employee.class)
                        .forEach(e -> {
                            received.add(e);
                            cancel.set(true); // cancel after the first row
                        }));

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(1, received.size());
        assertEquals(1, ex.outcome().rowsReturnedBeforeAbort());
    }

    @Test
    void midIteratorCancellationShouldCarryExactRowsReturnedBeforeAbort() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        List<Employee> received = new ArrayList<>();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> {
                    Iterator<Employee> it = PojoLensSql.parse("where active = true")
                            .executionGuard(guard)
                            .iterator(sampleEmployees(), Employee.class);
                    while (it.hasNext()) {
                        received.add(it.next());
                        cancel.set(true); // cancel after the first row
                    }
                });

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(1, received.size());
        assertEquals(1, ex.outcome().rowsReturnedBeforeAbort());
    }

    // --- SqlLikeQuery: cancel-only guard with no other limits ---

    @Test
    void cancelOnlyGuardWithoutOtherLimitsShouldNotBlockWhenNotCancelled() {
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(() -> false)
                .build();

        List<Employee> result = PojoLensSql.parse("where salary > 100000")
                .executionGuard(guard)
                .filter(sampleEmployees(), Employee.class);

        assertFalse(result.isEmpty());
    }

    @Test
    void boundFilterShouldBlockWhenCancelledAfterBind() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        SqlLikeBoundQuery<Employee> bound = PojoLensSql.parse("where active = true")
                .executionGuard(guard)
                .bindTyped(sampleEmployees(), Employee.class);

        cancel.set(true);
        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class, bound::filter);

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(0, ex.outcome().rowsReturnedBeforeAbort());
    }

    @Test
    void boundChartShouldBlockWhenCancelledAfterBind() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        SqlLikeBoundQuery<DepartmentCount> bound = PojoLensSql.parse(
                "select department, count(*) as total group by department")
                .executionGuard(guard)
                .bindTyped(sampleEmployees(), DepartmentCount.class);
        ChartSpec spec = ChartSpec.of(ChartType.BAR, "department", "total");

        cancel.set(true);
        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> bound.chart(spec));

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(0, ex.outcome().rowsReturnedBeforeAbort());
    }

    // --- SqlLikeQuery: telemetry emitted on cancellation ---

    @Test
    void preExecutionCancellationShouldEmitGuardRejectedTelemetry() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        List<QueryTelemetryEvent> events = new ArrayList<>();

        assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true")
                        .telemetry(events::add)
                        .executionGuard(guard)
                        .filter(sampleEmployees(), Employee.class));

        List<QueryTelemetryEvent> guardEvents = events.stream()
                .filter(e -> e.stage() == QueryTelemetryStage.GUARD_REJECTED)
                .toList();
        assertEquals(1, guardEvents.size());
        assertEquals("GUARD_CANCELLED", guardEvents.get(0).metadata().get("guardBlockCode"));
    }

    @Test
    void midStreamCancellationShouldEmitGuardRejectedTelemetry() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        List<QueryTelemetryEvent> events = new ArrayList<>();

        assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true")
                        .telemetry(events::add)
                        .executionGuard(guard)
                        .stream(sampleEmployees(), Employee.class)
                        .forEach(e -> cancel.set(true)));

        List<QueryTelemetryEvent> guardEvents = events.stream()
                .filter(e -> e.stage() == QueryTelemetryStage.GUARD_REJECTED)
                .toList();
        assertEquals(1, guardEvents.size());
        assertEquals("GUARD_CANCELLED", guardEvents.get(0).metadata().get("guardBlockCode"));
        assertNotNull(guardEvents.get(0).metadata().get("rowsReturnedBeforeAbort"));
    }

    // --- NaturalQuery cancellation ---

    @Test
    void naturalQueryWithPreCancelledTokenShouldBlock() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> NaturalQuery.of("show employees where active is true")
                        .executionGuard(guard)
                        .filter(sampleEmployees(), Employee.class));

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(0, ex.outcome().rowsReturnedBeforeAbort());
    }

    // --- TypedQuery cancellation ---

    @Test
    void typedQueryWithPreCancelledTokenShouldBlock() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        TypedField<Employee, Boolean> active = TypedField.of("active", Boolean.class);

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> TypedQuery.from(Employee.class)
                        .where(active.eq(true))
                        .executionGuard(guard)
                        .filter(sampleEmployees()));

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(0, ex.outcome().rowsReturnedBeforeAbort());
    }

    @Test
    void typedQueryWithPreCancelledTokenShouldBlockEmptyInput() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> TypedQuery.from(Employee.class)
                        .executionGuard(guard)
                        .filter(List.of()));

        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
        assertEquals(0, ex.outcome().rowsReturnedBeforeAbort());
    }

    @Test
    void typedQueryWithNonFiredTokenShouldAllowExecution() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        TypedField<Employee, Boolean> active = TypedField.of("active", Boolean.class);

        List<Employee> result = TypedQuery.from(Employee.class)
                .where(active.eq(true))
                .executionGuard(guard)
                .filter(sampleEmployees());

        assertEquals(3, result.size());
    }

    // --- Combined: cancellation token + other limits ---

    @Test
    void cancelledTokenShouldTakePrecedenceOverRowScanLimit() {
        AtomicBoolean cancel = new AtomicBoolean(true);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .maxRowsScanned(1000)
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();

        QueryExecutionGuardException ex = assertThrows(QueryExecutionGuardException.class,
                () -> PojoLensSql.parse("where active = true")
                        .executionGuard(guard)
                        .filter(sampleEmployees(), Employee.class));

        // Cancellation is checked first — before plan preview
        assertEquals("GUARD_CANCELLED", ex.outcome().blockCode());
    }

    public static class DepartmentCount {
        public String department;
        public long total;
    }
}
