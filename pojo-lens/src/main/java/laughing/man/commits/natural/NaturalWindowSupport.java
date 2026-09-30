package laughing.man.commits.natural;

import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.internal.builder.QueryWindowFrame;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.internal.window.WindowExpressionText;

import java.util.List;

public final class NaturalWindowSupport {

    private static final int DEFAULT_OFFSET = 1;

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
        return WindowExpressionText.render(function, valueField, countAll, DEFAULT_OFFSET, null,
                partitionFields, orderFields, frame);
    }

    /**
     * Renders a window expression including {@code LAG}/{@code LEAD} offset arguments.
     */
    public static String renderWindowExpression(String function,
                                                String valueField,
                                                boolean countAll,
                                                int offset,
                                                Object defaultValue,
                                                List<String> partitionFields,
                                                List<OrderAst> orderFields,
                                                QueryWindowFrame frame) {
        return WindowExpressionText.render(function, valueField, countAll, offset, defaultValue,
                partitionFields, orderFields, frame);
    }

    public static boolean isAggregateWindowFunction(String function) {
        WindowFunction resolved = WindowFunction.fromName(function);
        return resolved != null && resolved.isAggregateFunction();
    }
}
