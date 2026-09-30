package laughing.man.commits.sqllike.internal.params;

import laughing.man.commits.internal.TextPattern;

import java.util.Objects;

/**
 * Bound named parameter value that preserves the original parameter name
 * for deterministic diagnostics after binding.
 *
 * <p>{@link #value()} is the caller-supplied value, used for type checks and
 * diagnostics. {@link #executionValue()} is what the engine compares against:
 * the same value, or its {@link TextPattern} regex for pattern parameters.
 */
public final class BoundParameterValue {

    private final String name;
    private final Object value;
    private final TextPattern pattern;

    public BoundParameterValue(String name, Object value) {
        this(name, value, null);
    }

    public BoundParameterValue(String name, Object value, TextPattern pattern) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.value = value;
        this.pattern = pattern;
    }

    public String name() {
        return name;
    }

    public Object value() {
        return value;
    }

    public Object executionValue() {
        if (pattern == null || value == null) {
            return value;
        }
        return pattern.toRegex(String.valueOf(value));
    }
}
