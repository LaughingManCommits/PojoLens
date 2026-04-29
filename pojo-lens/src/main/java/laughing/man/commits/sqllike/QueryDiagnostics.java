package laughing.man.commits.sqllike;

import java.util.List;
import java.util.Objects;

/**
 * Pre-execution diagnostics result for a SQL-like query.
 * <p>
 * Captures structural metadata, lint warnings, and validation findings
 * without executing the query against rows. Use {@link SqlLikeQuery#diagnostics()}
 * for parse-level info or {@link SqlLikeQuery#diagnostics(Class, Class)} to
 * include field and source validation.
 */
public final class QueryDiagnostics {

    private final boolean valid;
    private final List<QueryDiagnosticsError> errors;
    private final List<SqlLikeLintWarning> lintWarnings;
    private final List<String> requiredParams;
    private final List<String> referencedFields;
    private final List<String> outputFields;
    private final List<String> joinSources;
    private final boolean hasSubqueries;

    public QueryDiagnostics(boolean valid,
                            List<QueryDiagnosticsError> errors,
                            List<SqlLikeLintWarning> lintWarnings,
                            List<String> requiredParams,
                            List<String> referencedFields,
                            List<String> outputFields,
                            List<String> joinSources,
                            boolean hasSubqueries) {
        this.valid = valid;
        this.errors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
        this.lintWarnings = List.copyOf(Objects.requireNonNull(lintWarnings, "lintWarnings must not be null"));
        this.requiredParams = List.copyOf(Objects.requireNonNull(requiredParams, "requiredParams must not be null"));
        this.referencedFields = List.copyOf(Objects.requireNonNull(referencedFields, "referencedFields must not be null"));
        this.outputFields = List.copyOf(Objects.requireNonNull(outputFields, "outputFields must not be null"));
        this.joinSources = List.copyOf(Objects.requireNonNull(joinSources, "joinSources must not be null"));
        this.hasSubqueries = hasSubqueries;
    }

    /**
     * Returns true when no validation or parse errors were found.
     *
     * @return true when the query passed all checked constraints
     */
    public boolean valid() {
        return valid;
    }

    /**
     * Returns validation and parse errors found during diagnostics.
     * Empty when {@link #valid()} is true.
     *
     * @return errors, empty for valid queries
     */
    public List<QueryDiagnosticsError> errors() {
        return errors;
    }

    /**
     * Returns non-blocking lint warnings for the query.
     *
     * @return lint warnings
     */
    public List<SqlLikeLintWarning> lintWarnings() {
        return lintWarnings;
    }

    /**
     * Returns named parameter placeholders required by the query,
     * without the {@code :} prefix.
     *
     * @return required parameter names
     */
    public List<String> requiredParams() {
        return requiredParams;
    }

    /**
     * Returns source field names referenced by WHERE, SELECT, GROUP BY,
     * ORDER BY, and JOIN clauses.
     *
     * @return referenced source field names
     */
    public List<String> referencedFields() {
        return referencedFields;
    }

    /**
     * Returns output field names produced by the SELECT clause.
     * Empty for wildcard {@code SELECT *}.
     *
     * @return output field names
     */
    public List<String> outputFields() {
        return outputFields;
    }

    /**
     * Returns named JOIN sources referenced by the query.
     *
     * @return join source names
     */
    public List<String> joinSources() {
        return joinSources;
    }

    /**
     * Returns true when the query uses IN-subquery or EXISTS subquery predicates.
     *
     * @return true when the query contains subqueries
     */
    public boolean hasSubqueries() {
        return hasSubqueries;
    }
}
