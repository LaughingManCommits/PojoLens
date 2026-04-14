package laughing.man.commits.natural;

import laughing.man.commits.builder.QueryWindowFrame;
import laughing.man.commits.sqllike.ast.OrderAst;

import java.util.List;

public final class NaturalWindowSupport {

    private NaturalWindowSupport() {
    }

    public static String renderWindowExpression(String function,
                                                String valueField,
                                                boolean countAll,
                                                List<String> partitionFields,
                                                List<OrderAst> orderFields) {
        return renderWindowExpression(
                function,
                valueField,
                countAll,
                partitionFields,
                orderFields,
                QueryWindowFrame.running()
        );
    }

    public static String renderWindowExpression(String function,
                                                String valueField,
                                                boolean countAll,
                                                List<String> partitionFields,
                                                List<OrderAst> orderFields,
                                                QueryWindowFrame frame) {
        StringBuilder expression = new StringBuilder(function).append('(');
        if (isAggregateWindowFunction(function)) {
            expression.append(countAll ? "*" : valueField);
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
        if (isAggregateWindowFunction(function)) {
            if (wroteSegment) {
                expression.append(' ');
            }
            expression.append((frame == null ? QueryWindowFrame.running() : frame).sqlExpression());
        }
        expression.append(')');
        return expression.toString();
    }

    public static boolean isAggregateWindowFunction(String function) {
        if (function == null) {
            return false;
        }
        return !"ROW_NUMBER".equalsIgnoreCase(function)
                && !"RANK".equalsIgnoreCase(function)
                && !"DENSE_RANK".equalsIgnoreCase(function);
    }
}
