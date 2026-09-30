package laughing.man.commits.sqllike.internal.window;

import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.internal.builder.QueryWindowFrame;
import laughing.man.commits.sqllike.ast.OrderAst;

import java.util.List;

/**
 * Single owner of canonical SQL-like window expression text, such as
 * {@code SUM(salary) OVER (PARTITION BY dept ORDER BY hired ASC ROWS BETWEEN ...)} or
 * {@code LAG(salary, 2, 0) OVER (ORDER BY hired ASC)}.
 */
public final class WindowExpressionText {

    private static final int DEFAULT_OFFSET = 1;

    private WindowExpressionText() {
    }

    /**
     * @param function     window function name, such as {@code ROW_NUMBER} or {@code LAG}
     * @param valueField   aggregate/offset argument, ignored for rank functions
     * @param offset       {@code LAG}/{@code LEAD} offset, ignored otherwise
     * @param defaultValue {@code LAG}/{@code LEAD} default literal, ignored otherwise
     * @param frame        aggregate {@code ROWS} frame, ignored otherwise
     */
    public static String render(String function,
                                String valueField,
                                boolean countAll,
                                int offset,
                                Object defaultValue,
                                List<String> partitionFields,
                                List<OrderAst> orderFields,
                                QueryWindowFrame frame) {
        WindowFunction resolved = WindowFunction.fromName(function);
        if (resolved == null) {
            throw new IllegalArgumentException("Unsupported window function '" + function + "'");
        }
        StringBuilder expression = new StringBuilder(function).append('(');
        if (resolved.isAggregateFunction()) {
            expression.append(countAll ? "*" : valueField);
        } else if (resolved.isOffsetFunction()) {
            appendOffsetArguments(expression, valueField, offset, defaultValue);
        }
        expression.append(") OVER (");
        boolean wroteSegment = false;
        if (partitionFields != null && !partitionFields.isEmpty()) {
            expression.append("PARTITION BY ").append(String.join(", ", partitionFields));
            wroteSegment = true;
        }
        if (orderFields != null && !orderFields.isEmpty()) {
            if (wroteSegment) {
                expression.append(' ');
            }
            expression.append("ORDER BY ");
            for (int i = 0; i < orderFields.size(); i++) {
                if (i > 0) {
                    expression.append(", ");
                }
                OrderAst order = orderFields.get(i);
                expression.append(order.field());
                if (order.sort() != null) {
                    expression.append(' ').append(order.sort().name());
                }
            }
            wroteSegment = true;
        }
        if (resolved.isAggregateFunction()) {
            if (wroteSegment) {
                expression.append(' ');
            }
            expression.append((frame == null ? QueryWindowFrame.running() : frame).sqlExpression());
        }
        expression.append(')');
        return expression.toString();
    }

    private static void appendOffsetArguments(StringBuilder expression,
                                              String valueField,
                                              int offset,
                                              Object defaultValue) {
        expression.append(valueField);
        if (offset == DEFAULT_OFFSET && defaultValue == null) {
            return;
        }
        expression.append(", ").append(offset);
        if (defaultValue != null) {
            expression.append(", ").append(literal(defaultValue));
        }
    }

    /**
     * SQL-like literal text for a {@code LAG}/{@code LEAD} default.
     */
    public static String literal(Object value) {
        if (value instanceof CharSequence || value instanceof Character) {
            return "'" + value.toString().replace("'", "''") + "'";
        }
        return String.valueOf(value);
    }
}
