package laughing.man.commits.sqllike.internal.aggregate;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared aggregate-expression parsing/canonicalization utilities
 * used across SQL-like binding and validation.
 */
public final class AggregateExpressionSupport {

    private AggregateExpressionSupport() {
    }

    public static Map<String, String> resolveSelectAggregateOutputAliases(SelectAst select) {
        Map<String, String> output = new LinkedHashMap<>();
        if (select == null || select.wildcard()) {
            return output;
        }
        for (SelectFieldAst field : select.fields()) {
            if (!field.metricField()) {
                continue;
            }
            output.put(canonicalFromSelectField(field), field.outputName());
        }
        return output;
    }

    public static String canonicalFromSelectField(SelectFieldAst field) {
        String argument = field.countAll() ? "*" : field.field();
        return field.metric().name().toLowerCase(Locale.ROOT) + "(" + argument + ")";
    }

    public static ParsedAggregateExpression parse(String reference) {
        if (reference == null) {
            return null;
        }
        int open = reference.indexOf('(');
        int close = reference.lastIndexOf(')');
        if (open <= 0 || close != reference.length() - 1) {
            return null;
        }
        String metricName = reference.substring(0, open).trim().toUpperCase(Locale.ROOT);
        String argument = reference.substring(open + 1, close).trim();
        Metric metric = metricFromName(metricName);
        if (metric == null) {
            return null;
        }
        if ("*".equals(argument)) {
            return new ParsedAggregateExpression(metric, true, "*");
        }
        if (argument.isBlank()) {
            return null;
        }
        return new ParsedAggregateExpression(metric, false, argument);
    }

    public static String canonicalFromReference(String reference, Set<String> sourceFields) {
        ParsedAggregateExpression parsed = parse(reference);
        if (parsed == null) {
            return null;
        }
        if (parsed.countAll()) {
            if (parsed.metric() == Metric.COUNT) {
                return "count(*)";
            }
            throw unknownHavingReference(reference);
        }
        String argument = parsed.field();
        if (argument == null || argument.isBlank() || !sourceFields.contains(argument)) {
            throw unknownHavingReference(reference);
        }
        return parsed.metric().name().toLowerCase(Locale.ROOT) + "(" + argument + ")";
    }

    public static String addHiddenHavingAggregate(QueryBuilder builder, ParsedAggregateExpression expression) {
        String alias = hiddenAggregateAlias("__having_expr", expression);
        if (expression.countAll()) {
            builder.addCount(alias);
        } else {
            builder.addMetric(expression.field(), expression.metric(), alias);
        }
        return alias;
    }

    public static String addHiddenOrderAggregate(QueryBuilder builder, ParsedAggregateExpression expression) {
        String alias = hiddenAggregateAlias("__order_expr", expression);
        if (expression.countAll()) {
            builder.addCount(alias);
        } else {
            builder.addMetric(expression.field(), expression.metric(), alias);
        }
        return alias;
    }

    private static String hiddenAggregateAlias(String prefix, ParsedAggregateExpression expression) {
        String canonical = expression.metric().name().toLowerCase(Locale.ROOT)
                + "("
                + (expression.countAll() ? "*" : expression.field())
                + ")";
        String sanitized = canonical.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        int hash = canonical.hashCode();
        String suffix = Integer.toHexString(hash);
        return prefix + "_" + sanitized + "_" + suffix;
    }

    private static Metric metricFromName(String metricName) {
        return switch (metricName) {
            case "COUNT" -> Metric.COUNT;
            case "SUM" -> Metric.SUM;
            case "AVG" -> Metric.AVG;
            case "MIN" -> Metric.MIN;
            case "MAX" -> Metric.MAX;
            default -> null;
        };
    }

    private static IllegalArgumentException unknownHavingReference(String reference) {
        return new IllegalArgumentException("Unknown HAVING reference '" + reference + "'");
    }

    public record ParsedAggregateExpression(Metric metric, boolean countAll, String field) {
    }
}

