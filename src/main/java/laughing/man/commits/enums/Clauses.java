package laughing.man.commits.enums;

/**
 * Comparison operators supported by query rules.
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
    IN;
}

