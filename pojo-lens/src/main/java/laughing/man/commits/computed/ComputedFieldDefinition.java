package laughing.man.commits.computed;

import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.util.StringUtil;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable named computed-field definition backed by a numeric expression.
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
        Class<?> normalizedOutputType = requireNumericOutputType(outputType);
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
            throw new IllegalArgumentException("expression must be a numeric expression or identifier");
        }
        return normalized;
    }

    private static Class<?> requireNumericOutputType(Class<?> outputType) {
        Objects.requireNonNull(outputType, "outputType must not be null");
        Class<?> wrapped = wrap(outputType);
        if (!Number.class.isAssignableFrom(wrapped)) {
            throw new IllegalArgumentException("computed field outputType must be numeric");
        }
        return wrapped;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        return type;
    }
}

