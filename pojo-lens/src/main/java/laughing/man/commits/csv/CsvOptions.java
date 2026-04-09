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

    private CsvOptions(Builder builder) {
        this.header = builder.header;
        this.delimiter = requireDelimiter(builder.delimiter);
        this.trim = builder.trim;
        this.skipEmptyLines = builder.skipEmptyLines;
    }

    public static CsvOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
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

        public CsvOptions build() {
            return new CsvOptions(this);
        }
    }
}
