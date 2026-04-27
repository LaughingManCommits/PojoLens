package laughing.man.commits.util;

import java.text.NumberFormat;
import java.text.ParsePosition;
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
     * Checks whether the full input string can be parsed as a number.
     */
    public static boolean isNumber(String str) {
        NumberFormat formatter = NumberFormat.getInstance();
        ParsePosition pos = new ParsePosition(0);
        formatter.parse(str, pos);
        return str.length() == pos.getIndex();
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

