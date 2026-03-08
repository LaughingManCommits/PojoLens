package laughing.man.commits.computed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable registry of named computed-field definitions.
 */
public final class ComputedFieldRegistry {

    private static final ComputedFieldRegistry EMPTY = new ComputedFieldRegistry(Collections.emptyMap());

    private final Map<String, ComputedFieldDefinition> definitions;

    private ComputedFieldRegistry(Map<String, ComputedFieldDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static ComputedFieldRegistry empty() {
        return EMPTY;
    }

    public static ComputedFieldRegistry of(ComputedFieldDefinition... definitions) {
        Builder builder = builder();
        if (definitions != null) {
            for (ComputedFieldDefinition definition : definitions) {
                builder.add(Objects.requireNonNull(definition, "definition must not be null"));
            }
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    public boolean contains(String name) {
        return definitions.containsKey(name);
    }

    public ComputedFieldDefinition get(String name) {
        return definitions.get(name);
    }

    public Set<String> names() {
        return definitions.keySet();
    }

    public Map<String, ComputedFieldDefinition> definitions() {
        return definitions;
    }

    public static final class Builder {
        private final LinkedHashMap<String, ComputedFieldDefinition> definitions = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder add(String name, String expression, Class<?> outputType) {
            return add(ComputedFieldDefinition.of(name, expression, outputType));
        }

        public Builder add(ComputedFieldDefinition definition) {
            Objects.requireNonNull(definition, "definition must not be null");
            if (definitions.containsKey(definition.name())) {
                throw new IllegalArgumentException("Duplicate computed field name: " + definition.name());
            }
            definitions.put(definition.name(), definition);
            return this;
        }

        public ComputedFieldRegistry build() {
            if (definitions.isEmpty()) {
                return EMPTY;
            }
            return new ComputedFieldRegistry(definitions);
        }
    }
}

