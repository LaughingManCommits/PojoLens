package laughing.man.commits.csv;

/**
 * Narrow CSV loader options for the typed boundary adapter.
 */
public final class CsvOptions {

    private static final CsvOptions DEFAULTS = builder().build();

    private final boolean header;
    private final char delimiter;
    private final boolean trim;
    private final boolean skipEmptyLines;
    private final CsvCoercionPolicy coercionPolicy;

    private CsvOptions(Builder builder) {
        this.header = builder.header;
        this.delimiter = requireDelimiter(builder.delimiter);
        this.trim = builder.trim;
        this.skipEmptyLines = builder.skipEmptyLines;
        if (builder.coercionPolicy == null) {
            throw new IllegalArgumentException("coercionPolicy must not be null");
        }
        this.coercionPolicy = builder.coercionPolicy;
    }

    public static CsvOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder()
                .header(header)
                .delimiter(delimiter)
                .trim(trim)
                .skipEmptyLines(skipEmptyLines)
                .coercionPolicy(coercionPolicy);
    }

    public boolean header() {
        return header;
    }

    public char delimiter() {
        return delimiter;
    }

    public boolean trim() {
        return trim;
    }

    public boolean skipEmptyLines() {
        return skipEmptyLines;
    }

    public CsvCoercionPolicy coercionPolicy() {
        return coercionPolicy;
    }

    private static char requireDelimiter(char delimiter) {
        if (delimiter == '\n' || delimiter == '\r' || delimiter == '"') {
            throw new IllegalArgumentException("delimiter must be a visible non-quote character");
        }
        return delimiter;
    }

    public static final class Builder {
        private boolean header = true;
        private char delimiter = ',';
        private boolean trim = true;
        private boolean skipEmptyLines = true;
        private CsvCoercionPolicy coercionPolicy = CsvCoercionPolicy.defaults();

        private Builder() {
        }

        public Builder header(boolean enabled) {
            this.header = enabled;
            return this;
        }

        public Builder delimiter(char value) {
            this.delimiter = value;
            return this;
        }

        public Builder trim(boolean enabled) {
            this.trim = enabled;
            return this;
        }

        public Builder skipEmptyLines(boolean enabled) {
            this.skipEmptyLines = enabled;
            return this;
        }

        public Builder coercionPolicy(CsvCoercionPolicy value) {
            this.coercionPolicy = value;
            return this;
        }

        public CsvOptions build() {
            return new CsvOptions(this);
        }
    }
}
