package laughing.man.commits.util;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * String helpers used by query parsing and comparison logic.
 */
public final class StringUtil {

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "NP_BOOLEAN_RETURN_NULL",
            justification = "Nullable tri-state return distinguishes no match from explicit true/false."
    )
    public static Boolean parseBoolStrict(String string) {
        if (string == null) {
            return null;
        }
        String normalized = string.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true")
                || normalized.equals("1")
                || normalized.equals("yes")
                || normalized.equals("on")) {
            return Boolean.TRUE;
        }
        if (normalized.equals("false")
                || normalized.equals("0")
                || normalized.equals("no")
                || normalized.equals("off")) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * Returns {@code true} when the value is {@code null} or empty.
     */
    public static boolean isNull(String str) {
        return !(str != null && !str.isEmpty());
    }

    /**
     * Returns {@code true} when the value is {@code null} or blank.
     */
    public static boolean isNullOrBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Checks whether the full input string is a plain decimal number
     * ({@code 42}, {@code -1.5}, {@code 1e3}); independent of the default locale.
     */
    public static boolean isNumber(String str) {
        return parseNumber(str) != null;
    }

    /**
     * Parses a plain decimal number ({@code 42}, {@code -1.5}, {@code 1e3}, surrounding
     * whitespace allowed) exactly, independent of the default locale; {@code null} when the
     * text is not a number. Grouping separators and locale decimal commas are rejected.
     */
    public static BigDecimal parseNumber(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    public static String requireNonBlank(String value, String label) {
        if (isNullOrBlank(value)) {
            throw new IllegalArgumentException(label + " must not be null/blank");
        }
        return value;
    }

    private StringUtil() {
    }
}

