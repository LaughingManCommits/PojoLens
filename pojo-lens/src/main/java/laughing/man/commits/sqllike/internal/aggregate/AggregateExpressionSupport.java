package laughing.man.commits.sqllike.internal.aggregate;

import laughing.man.commits.internal.builder.QueryBuilder;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.internal.NameSuggestions;
import laughing.man.commits.internal.NumericStatistics;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.internal.error.SqlLikeFieldMessages;
import laughing.man.commits.sqllike.ast.SelectFieldAst;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
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
        return canonical(field.metric(), argument, field.metricArgument());
    }

    /**
     * {@link #canonical(Metric, String)} with the {@code PERCENTILE} fraction:
     * {@code percentile(salary, 0.9)}.
     */
    public static String canonical(Metric metric, String field, Double metricArgument) {
        if (metric == Metric.PERCENTILE && metricArgument != null) {
            return "percentile(" + field + ", " + formatFraction(metricArgument) + ")";
        }
        return canonical(metric, field);
    }

    private static String formatFraction(double fraction) {
        return BigDecimal.valueOf(fraction).stripTrailingZeros().toPlainString();
    }

    /**
     * Adds a metric to the builder, routing {@code PERCENTILE} through
     * {@link QueryBuilder#addPercentile(String, double, String)}.
     */
    public static void addMetric(QueryBuilder builder, String field, Metric metric, Double argument, String alias) {
        if (metric == Metric.PERCENTILE) {
            builder.addPercentile(field, NumericStatistics.requirePercentile(argument), alias);
        } else {
            builder.addMetric(field, metric, alias);
        }
    }

    /**
     * Canonical aggregate text used to match select, HAVING, and ORDER BY references:
     * {@code sum(salary)}, {@code count(*)}, {@code count(distinct department)}.
     */
    public static String canonical(Metric metric, String argument) {
        if (metric == Metric.COUNT_DISTINCT) {
            return "count(distinct " + argument + ")";
        }
        return metric.name().toLowerCase(Locale.ROOT) + "(" + argument + ")";
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
        String distinctArgument = distinctArgument(metric, argument);
        if (distinctArgument != null) {
            return new ParsedAggregateExpression(Metric.COUNT_DISTINCT, false, distinctArgument);
        }
        if (metric == Metric.PERCENTILE) {
            return parsePercentile(argument);
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
            throw unknownHavingReference(reference, argument, sourceFields);
        }
        return canonical(parsed.metric(), argument, parsed.argument());
    }

    /**
     * {@code percentile(field, fraction)}; {@code null} when the argument is not that shape.
     */
    private static ParsedAggregateExpression parsePercentile(String argument) {
        int comma = argument.indexOf(',');
        if (comma <= 0) {
            return null;
        }
        String field = argument.substring(0, comma).trim();
        try {
            double fraction = Double.parseDouble(argument.substring(comma + 1).trim());
            return new ParsedAggregateExpression(Metric.PERCENTILE, false, field, fraction);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * The field of {@code count(distinct field)}, or {@code null} for any other argument.
     */
    private static String distinctArgument(Metric metric, String argument) {
        if (metric != Metric.COUNT || argument.length() <= "distinct ".length()
                || !argument.regionMatches(true, 0, "distinct ", 0, "distinct ".length())) {
            return null;
        }
        String field = argument.substring("distinct ".length()).trim();
        return field.isEmpty() || field.contains(" ") ? null : field;
    }

    public static String addHiddenHavingAggregate(QueryBuilder builder, ParsedAggregateExpression expression) {
        String alias = hiddenAggregateAlias("__having_expr", expression);
        if (expression.countAll()) {
            builder.addCount(alias);
        } else {
            addMetric(builder, expression.field(), expression.metric(), expression.argument(), alias);
        }
        return alias;
    }

    public static String addHiddenOrderAggregate(QueryBuilder builder, ParsedAggregateExpression expression) {
        String alias = hiddenAggregateAlias("__order_expr", expression);
        if (expression.countAll()) {
            builder.addCount(alias);
        } else {
            addMetric(builder, expression.field(), expression.metric(), expression.argument(), alias);
        }
        return alias;
    }

    private static String hiddenAggregateAlias(String prefix, ParsedAggregateExpression expression) {
        String canonical = canonical(expression.metric(), expression.countAll() ? "*" : expression.field(),
                expression.argument());
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
            case "MEDIAN" -> Metric.MEDIAN;
            case "PERCENTILE" -> Metric.PERCENTILE;
            case "STDDEV", "STDDEV_SAMP" -> Metric.STDDEV;
            case "STDDEV_POP" -> Metric.STDDEV_POP;
            case "VARIANCE", "VAR_SAMP" -> Metric.VARIANCE;
            case "VAR_POP" -> Metric.VAR_POP;
            default -> null;
        };
    }

    private static IllegalArgumentException unknownHavingReference(String reference) {
        return new IllegalArgumentException("Unknown HAVING reference '" + reference + "'");
    }

    private static IllegalArgumentException unknownHavingReference(String reference,
                                                                    String argument,
                                                                    Set<String> sourceFields) {
        String base = "Unknown HAVING reference '" + reference + "'.";
        if (argument != null && !argument.isBlank() && !sourceFields.isEmpty()) {
            List<String> suggestions = NameSuggestions.suggest(argument, sourceFields);
            return new IllegalArgumentException(base
                    + NameSuggestions.formatFragment(suggestions)
                    + SqlLikeFieldMessages.allowedSourceFieldsFragment(sourceFields));
        }
        return new IllegalArgumentException(base);
    }

    /**
     * @param argument the {@code PERCENTILE} fraction, or {@code null}
     */
    public record ParsedAggregateExpression(Metric metric, boolean countAll, String field, Double argument) {

        public ParsedAggregateExpression(Metric metric, boolean countAll, String field) {
            this(metric, countAll, field, null);
        }
    }
}

