package laughing.man.commits.sqllike.internal.expression;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExpressionEvaluatorTest {

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
}
