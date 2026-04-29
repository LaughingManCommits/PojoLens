package laughing.man.commits.files;

/**
 * Narrow JSON and JSONL loader options for the typed file-boundary adapter.
 */
public final class JsonOptions {

    private static final JsonOptions DEFAULTS = builder().build();

    private final boolean allowSingleObject;
    private final boolean skipEmptyLines;
    private final boolean failOnUnknownProperties;
    private final boolean enumCaseInsensitive;

    private JsonOptions(Builder builder) {
        this.allowSingleObject = builder.allowSingleObject;
        this.skipEmptyLines = builder.skipEmptyLines;
        this.failOnUnknownProperties = builder.failOnUnknownProperties;
        this.enumCaseInsensitive = builder.enumCaseInsensitive;
    }

    public static JsonOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public boolean allowSingleObject() {
        return allowSingleObject;
    }

    public boolean skipEmptyLines() {
        return skipEmptyLines;
    }

    public boolean failOnUnknownProperties() {
        return failOnUnknownProperties;
    }

    public boolean enumCaseInsensitive() {
        return enumCaseInsensitive;
    }

    public static final class Builder {
        private boolean allowSingleObject = true;
        private boolean skipEmptyLines = true;
        private boolean failOnUnknownProperties = true;
        private boolean enumCaseInsensitive;

        private Builder() {
        }

        private Builder(JsonOptions options) {
            this.allowSingleObject = options.allowSingleObject;
            this.skipEmptyLines = options.skipEmptyLines;
            this.failOnUnknownProperties = options.failOnUnknownProperties;
            this.enumCaseInsensitive = options.enumCaseInsensitive;
        }

        public Builder allowSingleObject(boolean enabled) {
            this.allowSingleObject = enabled;
            return this;
        }

        public Builder skipEmptyLines(boolean enabled) {
            this.skipEmptyLines = enabled;
            return this;
        }

        public Builder failOnUnknownProperties(boolean enabled) {
            this.failOnUnknownProperties = enabled;
            return this;
        }

        public Builder enumCaseInsensitive(boolean enabled) {
            this.enumCaseInsensitive = enabled;
            return this;
        }

        public JsonOptions build() {
            return new JsonOptions(this);
        }
    }
}
