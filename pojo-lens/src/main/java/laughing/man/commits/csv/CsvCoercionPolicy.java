package laughing.man.commits.csv;

import laughing.man.commits.util.StringUtil;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Opt-in coercion settings for CSV boundary loading.
 */
public final class CsvCoercionPolicy {

    private static final CsvCoercionPolicy DEFAULTS = builder().build();

    private final boolean blankStringAsNull;
    private final List<String> nullTokens;
    private final Set<String> normalizedNullTokens;
    private final boolean enumCaseInsensitive;
    private final boolean numericNormalizationEnabled;
    private final char decimalSeparator;
    private final char groupingSeparator;
    private final List<String> datePatterns;
    private final List<DateTimeFormatter> dateFormatters;
    private final List<String> dateTimePatterns;
    private final List<DateTimeFormatter> dateTimeFormatters;

    private CsvCoercionPolicy(Builder builder) {
        this.blankStringAsNull = builder.blankStringAsNull;
        this.nullTokens = List.copyOf(builder.nullTokens);
        this.normalizedNullTokens = Set.copyOf(builder.nullTokens);
        this.enumCaseInsensitive = builder.enumCaseInsensitive;
        this.numericNormalizationEnabled = builder.numericNormalizationEnabled;
        this.decimalSeparator = requireSeparator(builder.decimalSeparator, "decimalSeparator");
        this.groupingSeparator = requireSeparator(builder.groupingSeparator, "groupingSeparator");
        if (decimalSeparator == groupingSeparator) {
            throw new IllegalArgumentException("groupingSeparator must differ from decimalSeparator");
        }
        this.datePatterns = List.copyOf(builder.datePatterns);
        this.dateFormatters = compileFormatters(builder.datePatterns);
        this.dateTimePatterns = List.copyOf(builder.dateTimePatterns);
        this.dateTimeFormatters = compileFormatters(builder.dateTimePatterns);
    }

    public static CsvCoercionPolicy defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public boolean blankStringAsNull() {
        return blankStringAsNull;
    }

    public List<String> nullTokens() {
        return nullTokens;
    }

    public boolean isNullToken(String value) {
        return value != null && normalizedNullTokens.contains(value);
    }

    public boolean enumCaseInsensitive() {
        return enumCaseInsensitive;
    }

    public boolean numericNormalizationEnabled() {
        return numericNormalizationEnabled;
    }

    public char decimalSeparator() {
        return decimalSeparator;
    }

    public char groupingSeparator() {
        return groupingSeparator;
    }

    public List<String> datePatterns() {
        return datePatterns;
    }

    public List<DateTimeFormatter> dateFormatters() {
        return dateFormatters;
    }

    public List<String> dateTimePatterns() {
        return dateTimePatterns;
    }

    public List<DateTimeFormatter> dateTimeFormatters() {
        return dateTimeFormatters;
    }

    private static List<DateTimeFormatter> compileFormatters(List<String> patterns) {
        ArrayList<DateTimeFormatter> formatters = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            formatters.add(DateTimeFormatter.ofPattern(pattern));
        }
        return List.copyOf(formatters);
    }

    private static char requireSeparator(char value, String name) {
        if (value == '"' || value == '\n' || value == '\r' || Character.isWhitespace(value) || Character.isISOControl(value)) {
            throw new IllegalArgumentException(name + " must be a visible non-quote character");
        }
        return value;
    }

    private static String requireToken(String value, String name) {
        if (StringUtil.isNullOrBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public static final class Builder {
        private boolean blankStringAsNull;
        private final LinkedHashSet<String> nullTokens = new LinkedHashSet<>();
        private boolean enumCaseInsensitive;
        private boolean numericNormalizationEnabled;
        private char decimalSeparator = '.';
        private char groupingSeparator = ',';
        private final ArrayList<String> datePatterns = new ArrayList<>();
        private final ArrayList<String> dateTimePatterns = new ArrayList<>();

        private Builder() {
        }

        private Builder(CsvCoercionPolicy policy) {
            this.blankStringAsNull = policy.blankStringAsNull;
            this.nullTokens.addAll(policy.nullTokens);
            this.enumCaseInsensitive = policy.enumCaseInsensitive;
            this.numericNormalizationEnabled = policy.numericNormalizationEnabled;
            this.decimalSeparator = policy.decimalSeparator;
            this.groupingSeparator = policy.groupingSeparator;
            this.datePatterns.addAll(policy.datePatterns);
            this.dateTimePatterns.addAll(policy.dateTimePatterns);
        }

        public Builder blankStringAsNull(boolean enabled) {
            this.blankStringAsNull = enabled;
            return this;
        }

        public Builder nullToken(String value) {
            this.nullTokens.add(requireToken(value, "nullToken"));
            return this;
        }

        public Builder enumCaseInsensitive(boolean enabled) {
            this.enumCaseInsensitive = enabled;
            return this;
        }

        public Builder decimalSeparator(char value) {
            this.decimalSeparator = requireSeparator(value, "decimalSeparator");
            this.numericNormalizationEnabled = true;
            return this;
        }

        public Builder groupingSeparator(char value) {
            this.groupingSeparator = requireSeparator(value, "groupingSeparator");
            this.numericNormalizationEnabled = true;
            return this;
        }

        public Builder datePattern(String value) {
            this.datePatterns.add(requireToken(value, "datePattern"));
            return this;
        }

        public Builder dateTimePattern(String value) {
            this.dateTimePatterns.add(requireToken(value, "dateTimePattern"));
            return this;
        }

        public CsvCoercionPolicy build() {
            return new CsvCoercionPolicy(this);
        }
    }
}
