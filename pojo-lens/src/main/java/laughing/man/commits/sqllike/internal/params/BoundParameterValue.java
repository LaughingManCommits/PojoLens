package laughing.man.commits.sqllike.internal.params;

import java.util.Objects;

/**
 * Bound named parameter value that preserves the original parameter name
 * for deterministic diagnostics after binding.
 */
public final class BoundParameterValue {

    private final String name;
    private final Object value;

    public BoundParameterValue(String name, Object value) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.value = value;
    }

    public String name() {
        return name;
    }

    public Object value() {
        return value;
    }
}

