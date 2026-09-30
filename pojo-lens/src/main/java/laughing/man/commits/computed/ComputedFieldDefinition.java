package laughing.man.commits.computed;

import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.StringUtil;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable named computed-field definition: an expression and the type its values are
 * stored as. Numeric output types keep the {@code double} expression lane; other output
 * types ({@code String}, date/time, enum, ...) hold the typed expression value. The output
 * type must fit the expression's result, checked here when the result type is known and
 * again against the source field types when a query uses the field.
 */
public final class ComputedFieldDefinition {

    private final String name;
    private final String expression;
    private final Class<?> outputType;
    private final Set<String> dependencies;

    private ComputedFieldDefinition(String name, String expression, Class<?> outputType, Set<String> dependencies) {
        this.name = name;
        this.expression = expression;
        this.outputType = outputType;
        this.dependencies = Set.copyOf(dependencies);
    }

    public static ComputedFieldDefinition of(String name, String expression, Class<?> outputType) {
        String normalizedName = requireIdentifier(name, "name");
        String normalizedExpression = requireExpression(expression);
        Class<?> normalizedOutputType = requireOutputType(normalizedName, normalizedExpression, outputType);
        return new ComputedFieldDefinition(
                normalizedName,
                normalizedExpression,
                normalizedOutputType,
                new LinkedHashSet<>(SqlExpressionEvaluator.collectIdentifiers(normalizedExpression))
        );
    }

    public String name() {
        return name;
    }

    public String expression() {
        return expression;
    }

    /**
     * The stored value type; primitives are boxed.
     */
    public Class<?> outputType() {
        return outputType;
    }

    public Set<String> dependencies() {
        return dependencies;
    }

    private static String requireIdentifier(String value, String label) {
        if (StringUtil.isNullOrBlank(value)) {
            throw new IllegalArgumentException(label + " must not be null/blank");
        }
        return value.trim();
    }

    private static String requireExpression(String expression) {
        if (StringUtil.isNullOrBlank(expression)) {
            throw new IllegalArgumentException("expression must not be null/blank");
        }
        String normalized = expression.trim();
        if (!SqlExpressionEvaluator.looksLikeExpression(normalized) && SqlExpressionEvaluator.collectIdentifiers(normalized).size() != 1) {
            throw new IllegalArgumentException("expression must be an expression or identifier");
        }
        return normalized;
    }

    private static Class<?> requireOutputType(String name, String expression, Class<?> outputType) {
        Class<?> boxed = ReflectionUtil.wrapPrimitive(Objects.requireNonNull(outputType, "outputType must not be null"));
        SqlExpressionEvaluator.requireOutputType("Computed field '" + name + "'",
                SqlExpressionEvaluator.resultType(expression, fieldName -> null), boxed);
        return boxed;
    }
}
