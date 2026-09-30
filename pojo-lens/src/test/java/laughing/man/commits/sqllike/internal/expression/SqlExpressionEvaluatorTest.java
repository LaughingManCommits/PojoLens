package laughing.man.commits.sqllike.internal.expression;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExpressionEvaluatorTest {

    private enum Level {
        JUNIOR,
        SENIOR
    }

    @Test
    void compileNumericShouldCacheCompiledExpressions() {
        SqlExpressionEvaluator.CompiledExpression compiled =
                SqlExpressionEvaluator.compileNumeric("salary + bonus");

        assertSame(compiled, SqlExpressionEvaluator.compileNumeric("salary + bonus"));
        assertEquals(
                java.util.List.of("salary", "bonus"),
                new ArrayList<>(compiled.identifiers())
        );
        assertEquals(135.0, compiled.evaluate(identifier -> Map.of("salary", 120, "bonus", 15).get(identifier)), 0.0001);
    }

    @Test
    void compiledExpressionsShouldHandleFunctionsAndUnaryOperators() {
        SqlExpressionEvaluator.CompiledExpression compiled =
                SqlExpressionEvaluator.compileNumeric("ROUND(ABS(delta) / 2)");

        assertEquals(java.util.List.of("delta"), new ArrayList<>(compiled.identifiers()));
        assertEquals(3.0, compiled.evaluate(identifier -> Map.of("delta", -5.2).get(identifier)), 0.0001);
    }

    @Test
    void compiledExpressionsShouldSupportBoundArrayEvaluation() {
        SqlExpressionEvaluator.CompiledExpression compiled =
                SqlExpressionEvaluator.compileNumeric("salary + bonus * multiplier");

        SqlExpressionEvaluator.BoundExpression bound = compiled.bind(new int[]{2, 0, 1});

        assertEquals(150.0, bound.evaluate(new Object[]{20, 1.5d, 120}), 0.0001);
    }

    @Test
    void compileNumericShouldValidateFunctionArity() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> SqlExpressionEvaluator.compileNumeric("ABS(salary, bonus)")
        );

        assertTrue(ex.getMessage().contains("Function ABS requires 1 argument(s)"));
    }

    @Test
    void publicEntryPointsShouldRejectNullExpressionsBeforeCacheAccess() {
        assertBlankExpression(() -> SqlExpressionEvaluator.compileNumeric(null));
        assertBlankExpression(() -> SqlExpressionEvaluator.collectIdentifiers(null));
        assertBlankExpression(() -> SqlExpressionEvaluator.rewriteIdentifiers(null, identifier -> identifier));
        assertBlankExpression(() -> SqlExpressionEvaluator.evaluateNumeric(null, identifier -> 1));
        assertBlankExpression(() -> SqlExpressionEvaluator.evaluate(null, identifier -> 1));
    }

    @Test
    void publicEntryPointsShouldRejectBlankExpressionsBeforeCacheAccess() {
        assertBlankExpression(() -> SqlExpressionEvaluator.compileNumeric("   "));
        assertBlankExpression(() -> SqlExpressionEvaluator.collectIdentifiers("   "));
        assertBlankExpression(() -> SqlExpressionEvaluator.rewriteIdentifiers("   ", identifier -> identifier));
        assertBlankExpression(() -> SqlExpressionEvaluator.evaluateNumeric("   ", identifier -> 1));
        assertBlankExpression(() -> SqlExpressionEvaluator.evaluate("   ", identifier -> 1));
    }

    @Test
    void textAndNullLiteralsAreNotIdentifiers() {
        assertEquals("it's Bob", eval("concat('it''s', ' ', name)", Map.of("name", "Bob")));
        assertEquals("x", eval("coalesce(NULL, 'x')", Map.of()));
        assertEquals(List.of("nickname"), new ArrayList<>(SqlExpressionEvaluator.collectIdentifiers("coalesce(nickname, null, 'n/a')")));
    }

    @Test
    void lowerAndUpperIgnoreTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals("istanbul", eval("lower(city)", Map.of("city", "ISTANBUL")));
            assertEquals("STRASSE", eval("upper(street)", Map.of("street", "straße")));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void trimStripsUnicodeWhitespace() {
        assertEquals("a b", eval("trim(value)", Map.of("value", "  a b\t\n")));
    }

    @Test
    void lengthCountsCodePoints() {
        assertEquals(2, eval("length(value)", Map.of("value", "a😀")));
        assertEquals(0, eval("length('')", Map.of()));
    }

    @Test
    void substringUsesOneBasedWindowSemantics() {
        assertEquals("bc", eval("substring('abc', 2)", Map.of()));
        assertEquals("a", eval("substring('abc', 0, 2)", Map.of()));
        assertEquals("a", eval("substring('abc', -1, 3)", Map.of()));
        assertEquals("", eval("substring('abc', 5)", Map.of()));
        assertEquals("", eval("substring('abc', 2, 0)", Map.of()));
        assertEquals("😀", eval("substring(value, 2, 1)", Map.of("value", "a😀b")));
    }

    @Test
    void substringRejectsNegativeCountsAndFractions() {
        assertTrue(evalError("substring('abc', 1, -1)", Map.of()).contains("must not be negative"));
        assertTrue(evalError("substring('abc', 1.5)", Map.of()).contains("whole number"));
    }

    @Test
    void concatSkipsNullsAndPrintsWholeNumbersWithoutFraction() {
        Map<String, Object> values = new HashMap<>();
        values.put("missing", null);
        values.put("id", 1);
        values.put("level", Level.SENIOR);

        assertEquals("#2", eval("concat('#', missing, id + 1)", values));
        assertEquals("SENIOR:0.5", eval("concat(level, ':', id / 2)", values));
        assertEquals("", eval("concat(missing)", values));
        assertEquals("NaN", eval("concat(ratio)", Map.of("ratio", Double.NaN)));
    }

    @Test
    void coalesceAndNullifFollowSqlNullRules() {
        Map<String, Object> values = new HashMap<>();
        values.put("nickname", null);
        values.put("name", "Ann");
        values.put("status", "n/a");

        assertEquals("Ann", eval("coalesce(nickname, name)", values));
        assertNull(eval("coalesce(nickname, null)", values));
        assertNull(eval("nullif(status, 'n/a')", values));
        assertEquals("Ann", eval("nullif(name, 'n/a')", values));
        assertNull(eval("nullif(nickname, 'x')", values));
        assertNull(eval("nullif(5, 5.0)", values));
    }

    @Test
    void functionsPropagateNullArguments() {
        Map<String, Object> values = new HashMap<>();
        values.put("missing", null);

        assertNull(eval("lower(missing)", values));
        assertNull(eval("length(missing)", values));
        assertNull(eval("substring(missing, 1)", values));
        assertNull(eval("substring('abc', missing)", values));
        assertNull(eval("length(missing) + 1", values));
    }

    @Test
    void textFunctionsReadEnumNamesAndRejectOtherValues() {
        assertEquals("senior", eval("lower(level)", Map.of("level", Level.SENIOR)));
        assertTrue(evalError("lower(age)", Map.of("age", 5)).contains("needs a text value, not Integer"));
    }

    @Test
    void textFunctionResultsFeedArithmetic() {
        SqlExpressionEvaluator.CompiledExpression compiled = SqlExpressionEvaluator.compileNumeric("length(name) * 2");

        assertEquals(10.0, compiled.evaluate(identifier -> "Alice"), 0.0001);
        assertEquals(10.0, compiled.bind(new int[]{0}).evaluate(new Object[]{"Alice"}), 0.0001);
    }

    @Test
    void compileNumericRejectsTextResults() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SqlExpressionEvaluator.compileNumeric("lower(name)"));

        assertTrue(ex.getMessage().contains("returns text (String), not a number"), ex::getMessage);
    }

    @Test
    void literalKindErrorsFailAtCompileTime() {
        assertTrue(compileError("'a' * 2").contains("needs a number, not text"));
        assertTrue(compileError("-'a'").contains("needs a number, not text"));
        assertTrue(compileError("lower(1)").contains("Function LOWER needs text, not a number"));
        assertTrue(compileError("substring('abc', 'x')").contains("Function SUBSTRING needs a number"));
        assertTrue(compileError("coalesce(1, 'x')").contains("must share one type"));
    }

    @Test
    void unknownFunctionsAndArityFailAtCompileTime() {
        assertEquals("Unsupported expression function 'reverse'", compileError("reverse(name)"));
        assertEquals("Function SUBSTRING requires 2 to 3 argument(s)", compileError("substring(name)"));
        assertEquals("Function COALESCE requires at least 1 argument(s)", compileError("coalesce()"));
        assertEquals("Function CEILING requires 1 argument(s)", compileError("ceiling(a, b)"));
        assertEquals("Unterminated text literal in expression", compileError("lower('abc)"));
    }

    @Test
    void resultTypeFollowsTheFunctionAndFieldTypes() {
        Map<String, Class<?>> types = Map.of(
                "name", String.class,
                "salary", int.class,
                "bonus", Integer.class,
                "total", Long.class,
                "level", Level.class,
                "grade", Level.class,
                "hired", Date.class,
                "left", Date.class);

        assertEquals(Double.class, type("salary * 1.1", types));
        assertEquals(String.class, type("lower(name)", types));
        assertEquals(Integer.class, type("length(name)", types));
        assertEquals(String.class, type("concat(name, salary)", types));
        assertEquals(Integer.class, type("coalesce(bonus, salary, 0)", types));
        assertEquals(Double.class, type("coalesce(bonus, total)", types));
        assertEquals(Double.class, type("coalesce(null, 0)", types));
        assertEquals(Level.class, type("coalesce(level, grade)", types));
        assertEquals(String.class, type("coalesce(level, 'NONE')", types));
        assertEquals(Date.class, type("coalesce(left, hired)", types));
        assertEquals(Integer.class, type("nullif(bonus, 0)", types));
        assertEquals(Object.class, type("coalesce(unknown, 0)", types));
        assertEquals(Object.class, type("coalesce(null)", types));
    }

    @Test
    void resultTypeRejectsKindMismatchesOnKnownFields() {
        Map<String, Class<?>> types = Map.of("name", String.class, "salary", int.class, "hired", Date.class);

        assertTrue(typeError("lower(salary)", types).contains("Function LOWER needs text, not a number (Integer)"));
        assertTrue(typeError("name * 2", types).contains("Operator '*' needs a number, not text (String)"));
        assertTrue(typeError("coalesce(salary, name)", types).contains("must share one type"));
        assertTrue(typeError("nullif(hired, 'x')", types).contains("must share one type"));
    }

    @Test
    void rewriteIdentifiersKeepsLiteralsAndFunctionNames() {
        String rewritten = SqlExpressionEvaluator.rewriteIdentifiers(
                "concat(first, 'a''b', null) ", identifier -> "t." + identifier);

        assertEquals("concat(t.first,'a''b',null)", rewritten);
        assertEquals("xa'b", eval(rewritten, Map.of("t.first", "x")));
    }

    @Test
    void canonicalTextIgnoresSpellingDifferences() {
        assertEquals("YEAR(hireDate)", SqlExpressionEvaluator.canonical("Year( hireDate )"));
        assertEquals(SqlExpressionEvaluator.canonical("a+b"), SqlExpressionEvaluator.canonical("((a + b))"));
        assertEquals(SqlExpressionEvaluator.canonical("x * 1"), SqlExpressionEvaluator.canonical("x*1.0"));
        assertEquals("CEIL(x)", SqlExpressionEvaluator.canonical("ceiling(x)"));
        assertEquals("CONCAT(name, 'it''s', NULL)", SqlExpressionEvaluator.canonical("concat(name,'it''s',null)"));
        assertEquals("MONTH(t, 'Europe/Amsterdam')", SqlExpressionEvaluator.canonical("month(t, 'Europe/Amsterdam')"));
        assertTrue(!SqlExpressionEvaluator.canonical("a - b").equals(SqlExpressionEvaluator.canonical("b - a")));
    }

    @Test
    void scalarExpressionsOnlyCallExpressionFunctions() {
        assertTrue(SqlExpressionEvaluator.isScalarExpression("lower(name)"));
        assertTrue(SqlExpressionEvaluator.isScalarExpression("salary * 2"));
        assertTrue(SqlExpressionEvaluator.isScalarExpression("year(hireDate) - 1"));
        assertTrue(!SqlExpressionEvaluator.isScalarExpression("name"));
        assertTrue(!SqlExpressionEvaluator.isScalarExpression("sum(salary)"));
        assertTrue(!SqlExpressionEvaluator.isScalarExpression("count(*)"));
        assertTrue(!SqlExpressionEvaluator.isScalarExpression("sum(a) / 2"));
        assertTrue(!SqlExpressionEvaluator.isScalarExpression("lower(:name)"));
    }

    @Test
    void coerceGivesOneJavaTypePerOutput() {
        assertEquals(0, SqlExpressionEvaluator.coerce(0.0d, Integer.class));
        assertEquals(3L, SqlExpressionEvaluator.coerce(3, Long.class));
        assertEquals("SENIOR", SqlExpressionEvaluator.coerce(Level.SENIOR, String.class));
        assertEquals("x", SqlExpressionEvaluator.coerce("x", Integer.class));
        assertEquals(1.5d, SqlExpressionEvaluator.coerce(1.5d, Object.class));
        assertNull(SqlExpressionEvaluator.coerce(null, Integer.class));
        assertNull(SqlExpressionEvaluator.coerceNumber(Double.NaN, Integer.class));
        assertEquals(new BigDecimal("2.5"), SqlExpressionEvaluator.coerceNumber(2.5d, BigDecimal.class));
        assertEquals(3, SqlExpressionEvaluator.coerceNumber(2.5d, int.class));
    }

    private static Object eval(String expression, Map<String, ?> values) {
        return SqlExpressionEvaluator.evaluate(expression,
                identifier -> values.containsKey(identifier) ? values.get(identifier) : SqlExpressionEvaluator.UNKNOWN_IDENTIFIER);
    }

    private static String evalError(String expression, Map<String, ?> values) {
        return assertThrows(IllegalArgumentException.class, () -> eval(expression, values)).getMessage();
    }

    private static String compileError(String expression) {
        return assertThrows(IllegalArgumentException.class, () -> SqlExpressionEvaluator.compile(expression)).getMessage();
    }

    private static Class<?> type(String expression, Map<String, Class<?>> types) {
        return SqlExpressionEvaluator.resultType(expression, types::get);
    }

    private static String typeError(String expression, Map<String, Class<?>> types) {
        return assertThrows(IllegalArgumentException.class, () -> type(expression, types)).getMessage();
    }

    private static void assertBlankExpression(Runnable invocation) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                invocation::run
        );
        assertEquals("Expression must not be blank", ex.getMessage());
    }
}
