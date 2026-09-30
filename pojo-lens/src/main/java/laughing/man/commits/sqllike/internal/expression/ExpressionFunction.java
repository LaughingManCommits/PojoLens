package laughing.man.commits.sqllike.internal.expression;

import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator.ValueResolver;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * An expression function (WP-29). Each implementation is a table with one row per function:
 * {@link ScalarFunction} for numeric, text, and null functions, and {@link DatePartFunction}
 * for date parts. Function names are contextual: a name only counts as a function when
 * {@code (} follows it.
 */
sealed interface ExpressionFunction permits ScalarFunction, DatePartFunction {

    /**
     * @return the function for a case-insensitive name or alias, or {@code null}
     */
    static ExpressionFunction find(String name) {
        String upperName = name.toUpperCase(Locale.ROOT);
        ExpressionFunction scalar = ScalarFunction.lookup(upperName);
        return scalar != null ? scalar : DatePartFunction.lookup(upperName);
    }

    String name();

    void requireArgumentCount(String calledName, int count);

    /**
     * Compile-time argument preparation, such as parsing a constant argument once.
     */
    default List<ExpressionNode> prepare(List<ExpressionNode> args) {
        return args;
    }

    /**
     * Typed value ({@code null} for SQL NULL).
     */
    Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver);

    /**
     * Numeric lane: {@code NaN} for SQL NULL.
     */
    double number(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver);

    /**
     * Static result type; see {@link ExpressionNode#type}.
     */
    Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes);
}
