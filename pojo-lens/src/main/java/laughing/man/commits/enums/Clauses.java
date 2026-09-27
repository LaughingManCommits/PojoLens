package laughing.man.commits.enums;

/**
 * Comparison operators supported by query rules.
 *
 * <p>Constants may be added in later releases (for example {@link #NOT_CONTAINS} and
 * {@link #NOT_MATCHES} in WP-26). Code that switches over {@code Clauses} should keep a
 * {@code default} branch.</p>
 */
public enum Clauses {

    /** Equality. */
    EQUAL,
    /** Less than. */
    SMALLER,
    /** Greater than. */
    BIGGER,
    /** String contains. */
    CONTAINS,
    /** Inequality. */
    NOT_EQUAL,
    /** Greater than or equal. */
    BIGGER_EQUAL,
    /** Less than or equal. */
    SMALLER_EQUAL,
    /** Not less than. */
    NOT_SMALLER,
    /** Not greater than. */
    NOT_BIGGER,
    /** Regex match. */
    MATCHES,
    /** Membership in a supplied value set. */
    IN,
    /**
     * Text does not contain the value. Like every value comparison, a null field never
     * matches.
     */
    NOT_CONTAINS,
    /**
     * Text does not fully match the regex. Like every value comparison, a null field never
     * matches.
     */
    NOT_MATCHES;
}

