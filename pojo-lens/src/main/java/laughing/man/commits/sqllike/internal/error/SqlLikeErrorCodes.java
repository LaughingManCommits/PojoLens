package laughing.man.commits.sqllike.internal.error;

import java.util.Locale;

/**
 * Stable SQL-like error code catalog.
 */
public final class SqlLikeErrorCodes {

    public static final String API_QUERY_NULL = "EQ-SQL-API-001";
    public static final String API_QUERY_BLANK = "EQ-SQL-API-002";

    public static final String PARSE_SYNTAX = "EQ-SQL-PAR-001";
    public static final String PARSE_QUERY_LENGTH = "EQ-SQL-PAR-002";
    public static final String PARSE_TOKEN_LIMIT = "EQ-SQL-PAR-003";
    public static final String PARSE_CLAUSE_LIMIT = "EQ-SQL-PAR-004";

    public static final String VALIDATION_UNKNOWN_FIELD = "EQ-SQL-VAL-001";
    public static final String VALIDATION_DUPLICATE_SELECT_OUTPUT = "EQ-SQL-VAL-002";
    public static final String VALIDATION_MISSING_JOIN_SOURCE = "EQ-SQL-VAL-003";
    public static final String VALIDATION_INVALID_JOIN_ROWS = "EQ-SQL-VAL-004";
    public static final String VALIDATION_HAVING_REFERENCE = "EQ-SQL-VAL-005";
    public static final String VALIDATION_AGGREGATION_SEMANTICS = "EQ-SQL-VAL-006";
    public static final String VALIDATION_COMPUTED_SELECT = "EQ-SQL-VAL-007";
    public static final String VALIDATION_TIME_BUCKET = "EQ-SQL-VAL-008";
    public static final String VALIDATION_EXPRESSION_REFERENCE = "EQ-SQL-VAL-009";
    public static final String VALIDATION_SUBQUERY = "EQ-SQL-VAL-010";
    public static final String VALIDATION_AMBIGUOUS_FIELD = "EQ-SQL-VAL-011";

    public static final String PARAM_MISSING = "EQ-SQL-PRM-001";
    public static final String PARAM_UNKNOWN = "EQ-SQL-PRM-002";
    public static final String PARAM_UNRESOLVED = "EQ-SQL-PRM-003";
    public static final String PARAM_NAME_INVALID = "EQ-SQL-PRM-004";
    public static final String PARAM_TYPE_MISMATCH = "EQ-SQL-PRM-005";

    public static final String CURSOR_ORDER_REQUIRED = "EQ-SQL-CUR-001";
    public static final String CURSOR_FIELD_MISMATCH = "EQ-SQL-CUR-002";
    public static final String CURSOR_TOKEN_INVALID = "EQ-SQL-CUR-003";
    public static final String CURSOR_VALUE_INVALID = "EQ-SQL-CUR-004";

    public static final String BIND_MIXED_ORDER_DIRECTIONS = "EQ-SQL-BIND-001";
    public static final String BIND_BOOLEAN_COMPLEXITY = "EQ-SQL-BIND-002";

    public static final String JOIN_DUPLICATE_SOURCE_BINDING = "EQ-SQL-JOIN-001";
    public static final String JOIN_SOURCE_NAME_INVALID = "EQ-SQL-JOIN-002";

    public static final String RUNTIME_ALIASED_PROJECTION_FAILED = "EQ-SQL-RUN-001";
    public static final String RUNTIME_EXPRESSION_IDENTIFIER_RESOLUTION_FAILED = "EQ-SQL-RUN-002";

    private SqlLikeErrorCodes() {
    }

    public static String troubleshootingLink(String code) {
        return "docs/sql-like.md#error-code-" + code.toLowerCase(Locale.ROOT);
    }
}

