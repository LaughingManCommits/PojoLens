package laughing.man.commits.sqllike;

/**
 * Stable warning codes for SQL-like lint mode.
 */
public final class SqlLikeLintCodes {

    public static final String SELECT_WILDCARD = "EQ-SQL-LINT-001";
    public static final String LIMIT_WITHOUT_ORDER = "EQ-SQL-LINT-002";
    public static final String INLINE_STRING_LITERAL = "EQ-SQL-LINT-003";

    private SqlLikeLintCodes() {
    }
}

