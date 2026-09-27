package laughing.man.commits.internal;

import java.util.regex.Pattern;

/**
 * SQL {@code LIKE} / {@code ILIKE} pattern lowered to a full-match regex.
 *
 * <p>{@code %} matches any run of characters (including none and line terminators),
 * {@code _} exactly one character, and everything else matches itself: literal runs are
 * quoted, so regex metacharacters have no special meaning. The escape character makes
 * the next character literal ({@code '100\%'}); a pattern that ends with it is invalid.
 * A {@code null} escape disables escaping. {@code ILIKE} ignores case with Unicode case
 * folding, like {@link LiteralMatchPattern#CONTAINS_IGNORE_CASE}.</p>
 *
 * @param ignoreCase true for {@code ILIKE}
 * @param escape     escape character, or {@code null} for none
 */
public record LikePattern(boolean ignoreCase, Character escape) implements TextPattern {

    /** Escape character when a query gives no {@code ESCAPE} clause. */
    public static final char DEFAULT_ESCAPE = '\\';

    @Override
    public String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder(ignoreCase ? "(?siu)" : "(?s)");
        StringBuilder literal = new StringBuilder();
        int length = pattern.length();
        for (int i = 0; i < length; i++) {
            char c = pattern.charAt(i);
            if (escape != null && c == escape) {
                if (i + 1 == length) {
                    throw new IllegalArgumentException(
                            "LIKE pattern must not end with the escape character '" + escape + "': " + pattern);
                }
                literal.append(pattern.charAt(++i));
            } else if (c == '%' || c == '_') {
                appendLiteral(regex, literal);
                regex.append(c == '%' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        appendLiteral(regex, literal);
        return regex.toString();
    }

    private static void appendLiteral(StringBuilder regex, StringBuilder literal) {
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
            literal.setLength(0);
        }
    }
}
