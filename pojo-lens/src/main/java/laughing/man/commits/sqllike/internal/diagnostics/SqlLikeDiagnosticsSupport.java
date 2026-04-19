package laughing.man.commits.sqllike.internal.diagnostics;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryDiagnosticsError;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.sqllike.ast.ExistsSubqueryValueAst;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.JoinAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.sqllike.internal.lint.SqlLikeLintSupport;
import laughing.man.commits.sqllike.internal.params.SqlLikeParameterSupport;
import laughing.man.commits.sqllike.internal.validation.SqlLikeValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal helpers for building {@link QueryDiagnostics} from SQL-like AST metadata.
 */
public final class SqlLikeDiagnosticsSupport {

    private SqlLikeDiagnosticsSupport() {
    }

    public static QueryDiagnostics buildFromAst(QueryAst ast, Set<String> suppressedLintCodes) {
        List<SqlLikeLintWarning> lintWarnings = SqlLikeLintSupport.warnings(ast, suppressedLintCodes);
        List<String> requiredParams = new ArrayList<>(SqlLikeParameterSupport.collectParameterNames(ast));
        List<String> referencedFields = collectReferencedFields(ast);
        List<String> outputFields = collectOutputFields(ast);
        List<String> joinSourceNames = collectJoinSources(ast);
        boolean subqueries = hasSubqueries(ast);
        return new QueryDiagnostics(
                true,
                Collections.emptyList(),
                lintWarnings,
                requiredParams,
                referencedFields,
                outputFields,
                joinSourceNames,
                subqueries
        );
    }

    public static QueryDiagnostics buildWithValidation(QueryAst ast,
                                                       Set<String> suppressedLintCodes,
                                                       Class<?> sourceClass,
                                                       Class<?> projectionClass,
                                                       Map<String, List<?>> joinSources,
                                                       ComputedFieldRegistry computedFieldRegistry) {
        List<SqlLikeLintWarning> lintWarnings = SqlLikeLintSupport.warnings(ast, suppressedLintCodes);
        List<String> requiredParams = new ArrayList<>(SqlLikeParameterSupport.collectParameterNames(ast));
        List<String> referencedFields = collectReferencedFields(ast);
        List<String> outputFields = collectOutputFields(ast);
        List<String> joinSourceNames = collectJoinSources(ast);
        boolean subqueries = hasSubqueries(ast);

        List<QueryDiagnosticsError> errors;
        try {
            SqlLikeValidator.validateForFilter(ast, sourceClass, projectionClass, joinSources, false, computedFieldRegistry);
            errors = Collections.emptyList();
        } catch (IllegalArgumentException ex) {
            errors = List.of(extractError(ex));
        }

        return new QueryDiagnostics(
                errors.isEmpty(),
                errors,
                lintWarnings,
                requiredParams,
                referencedFields,
                outputFields,
                joinSourceNames,
                subqueries
        );
    }

    private static QueryDiagnosticsError extractError(IllegalArgumentException ex) {
        String fullMessage = ex.getMessage();
        if (fullMessage == null) {
            return new QueryDiagnosticsError("EQ-SQL-ERR", "Validation failed");
        }
        int colonIdx = fullMessage.indexOf(": ");
        if (colonIdx > 0) {
            String candidate = fullMessage.substring(0, colonIdx);
            if (candidate.startsWith("EQ-SQL-")) {
                String afterCode = fullMessage.substring(colonIdx + 2);
                int troubleshootIdx = afterCode.lastIndexOf(" Troubleshooting: ");
                String message = troubleshootIdx >= 0 ? afterCode.substring(0, troubleshootIdx) : afterCode;
                return new QueryDiagnosticsError(candidate, message);
            }
        }
        return new QueryDiagnosticsError("EQ-SQL-ERR", fullMessage);
    }

    public static List<String> collectReferencedFields(QueryAst ast) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();

        if (ast.select() != null && !ast.select().wildcard()) {
            for (SelectFieldAst field : ast.select().fields()) {
                if (field.computedField()) {
                    continue;
                }
                if (field.windowField()) {
                    if (field.windowValueField() != null && !field.windowCountAll()) {
                        fields.add(field.windowValueField());
                    }
                    fields.addAll(field.windowPartitionFields());
                    for (OrderAst order : field.windowOrderFields()) {
                        fields.add(order.field());
                    }
                    continue;
                }
                if (!field.countAll()) {
                    fields.add(field.field());
                }
            }
        }

        for (FilterAst filter : ast.filters()) {
            if (!(filter.value() instanceof SubqueryValueAst)
                    && !(filter.value() instanceof ExistsSubqueryValueAst)) {
                fields.add(filter.field());
            }
        }

        fields.addAll(ast.groupByFields());

        for (OrderAst order : ast.orders()) {
            fields.add(order.field());
        }

        for (JoinAst join : ast.joins()) {
            fields.add(join.parentField());
        }

        return List.copyOf(fields);
    }

    public static List<String> collectOutputFields(QueryAst ast) {
        if (ast.select() == null || ast.select().wildcard()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (SelectFieldAst field : ast.select().fields()) {
            names.add(field.outputName());
        }
        return List.copyOf(names);
    }

    public static List<String> collectJoinSources(QueryAst ast) {
        if (ast.joins().isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (JoinAst join : ast.joins()) {
            sources.add(join.childSource());
        }
        return List.copyOf(sources);
    }

    public static boolean hasSubqueries(QueryAst ast) {
        for (FilterAst filter : ast.filters()) {
            if (filter.value() instanceof SubqueryValueAst
                    || filter.value() instanceof ExistsSubqueryValueAst) {
                return true;
            }
        }
        return false;
    }
}
