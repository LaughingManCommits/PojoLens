package laughing.man.commits.internal;

import java.util.regex.Pattern;

/**
 * Single owner for literal string matches lowered to {@code MATCHES} regexes.
 *
 * <p>{@code MATCHES} evaluates with {@link java.util.regex.Matcher#matches()}, so each
 * pattern covers the whole value. {@code (?s)} lets {@code .*} span line terminators,
 * keeping results equal to the {@link String} method each constant names. The literal is
 * quoted, so regex metacharacters in it match themselves.
 */
public enum LiteralMatchPattern implements TextPattern {

    /** Same result as {@link String#startsWith(String)}. */
    STARTS_WITH {
        @Override
        public String toRegex(String literal) {
            return "(?s)^" + Pattern.quote(literal) + ".*";
        }
    },

    /** Same result as {@link String#endsWith(String)}. */
    ENDS_WITH {
        @Override
        public String toRegex(String literal) {
            return "(?s).*" + Pattern.quote(literal) + "$";
        }
    },

    /** Case-insensitive substring match, including non-ASCII letters. */
    CONTAINS_IGNORE_CASE {
        @Override
        public String toRegex(String literal) {
            return "(?siu).*" + Pattern.quote(literal) + ".*";
        }
    },

    /** Case-insensitive {@link #STARTS_WITH}, including non-ASCII letters. */
    STARTS_WITH_IGNORE_CASE {
        @Override
        public String toRegex(String literal) {
            return "(?siu)^" + Pattern.quote(literal) + ".*";
        }
    },

    /** Case-insensitive {@link #ENDS_WITH}, including non-ASCII letters. */
    ENDS_WITH_IGNORE_CASE {
        @Override
        public String toRegex(String literal) {
            return "(?siu).*" + Pattern.quote(literal) + "$";
        }
    };

    @Override
    public abstract String toRegex(String literal);
}
