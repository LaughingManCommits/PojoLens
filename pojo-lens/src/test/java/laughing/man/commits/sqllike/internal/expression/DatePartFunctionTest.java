package laughing.man.commits.sqllike.internal.expression;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-29 slice 2: date-part functions.
 */
class DatePartFunctionTest {

    @Test
    void partsOfALocalDateTime() {
        LocalDateTime sunday = LocalDateTime.of(2026, 3, 29, 1, 30);

        assertEquals(2026, part("year", sunday));
        assertEquals(1, part("quarter", sunday));
        assertEquals(3, part("month", sunday));
        assertEquals(29, part("day", sunday));
        assertEquals(1, part("hour", sunday));
        assertEquals(30, part("minute", sunday));
        assertEquals(7, part("day_of_week", sunday));
        assertEquals(1, part("DAY_OF_WEEK", LocalDate.of(2026, 3, 30)));
    }

    @Test
    void localDatesHaveMidnightTimeParts() {
        LocalDate date = LocalDate.of(2025, 11, 5);

        assertEquals(4, part("quarter", date));
        assertEquals(0, part("hour", date));
        assertEquals(0, part("minute", date));
    }

    @Test
    void instantsReadInUtcUnlessAZoneIsGiven() {
        Instant newYearsEve = Instant.parse("2026-12-31T23:30:00Z");

        assertEquals(2026, part("year", newYearsEve));
        assertEquals(2027, eval("year(t, 'Asia/Tokyo')", newYearsEve));
        assertEquals(1, eval("day(t, 'Asia/Tokyo')", newYearsEve));
        assertEquals(2026, part("year", Date.from(newYearsEve)));
        assertEquals(23, part("hour", ZonedDateTime.ofInstant(newYearsEve, ZoneId.of("Asia/Tokyo"))));
    }

    @Test
    void offsetValuesConvertIntoTheZoneLikeBuckets() {
        OffsetDateTime earlyMorning = OffsetDateTime.of(2026, 1, 1, 2, 0, 0, 0, ZoneOffset.ofHours(5));

        assertEquals(2025, part("year", earlyMorning));
        assertEquals(21, part("hour", earlyMorning));
        assertEquals(2, eval("hour(t, '+05:00')", earlyMorning));
    }

    @Test
    void nullValuesGiveNull() {
        Map<String, Object> values = new HashMap<>();
        values.put("t", null);

        assertNull(SqlExpressionEvaluator.evaluate("year(t)", values::get));
        assertNull(SqlExpressionEvaluator.evaluate("month(t) + 1", values::get));
    }

    @Test
    void datePartsFeedArithmetic() {
        SqlExpressionEvaluator.CompiledExpression compiled = SqlExpressionEvaluator.compileNumeric("year(t) - 2000");

        assertEquals(26.0, compiled.evaluate(identifier -> LocalDate.of(2026, 1, 1)), 0.0);
        assertEquals(26.0, compiled.bind(new int[]{0}).evaluate(new Object[]{LocalDate.of(2026, 1, 1)}), 0.0);
    }

    @Test
    void zoneMustBeAValidTextLiteral() {
        assertEquals("Unsupported time zone 'Mars/Base'", compileError("year(t, 'Mars/Base')"));
        assertEquals("Function MONTH zone must be a text literal such as 'Europe/Amsterdam'",
                compileError("month(t, zone)"));
        assertEquals("Function YEAR requires 1 to 2 argument(s)", compileError("year()"));
        assertEquals("Function DAY requires 1 to 2 argument(s)", compileError("day(t, 'UTC', 'x')"));
    }

    @Test
    void nonTemporalValuesAreRejected() {
        IllegalArgumentException runtime = assertThrows(IllegalArgumentException.class,
                () -> SqlExpressionEvaluator.evaluate("year(t)", identifier -> "2026-01-01"));
        assertEquals("Function YEAR needs a date/time value, not String", runtime.getMessage());

        Map<String, Class<?>> types = Map.of("name", String.class, "shift", LocalTime.class, "hired", Date.class);
        assertTrue(typeError("year(name)", types).contains("Function YEAR needs a date/time value"));
        assertTrue(typeError("hour(shift)", types).contains("not a date/time (LocalTime)"));
        assertTrue(compileError("month(5)").contains("not a number"));
        assertEquals(Integer.class, SqlExpressionEvaluator.resultType("quarter(hired)", types::get));
        assertEquals(Integer.class, SqlExpressionEvaluator.resultType("quarter(unknown)", types::get));
    }

    @Test
    void datePartNamesAreNotReserved() {
        assertEquals(List.of("year", "month"),
                new ArrayList<>(SqlExpressionEvaluator.collectIdentifiers("year * 100 + month")));
        assertEquals(List.of("hired"),
                new ArrayList<>(SqlExpressionEvaluator.collectIdentifiers("year(hired, 'Europe/Amsterdam')")));
    }

    private static Object part(String function, Object value) {
        return eval(function + "(t)", value);
    }

    private static Object eval(String expression, Object value) {
        return SqlExpressionEvaluator.evaluate(expression, identifier -> value);
    }

    private static String compileError(String expression) {
        return assertThrows(IllegalArgumentException.class, () -> SqlExpressionEvaluator.compile(expression)).getMessage();
    }

    private static String typeError(String expression, Map<String, Class<?>> types) {
        return assertThrows(IllegalArgumentException.class,
                () -> SqlExpressionEvaluator.resultType(expression, types::get)).getMessage();
    }
}
