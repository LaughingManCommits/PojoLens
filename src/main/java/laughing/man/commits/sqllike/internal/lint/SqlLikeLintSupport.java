package laughing.man.commits.sqllike.internal.lint;

import laughing.man.commits.sqllike.SqlLikeLintCodes;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic SQL-like lint warning helpers.
 */
public final class SqlLikeLintSupport {

    private SqlLikeLintSupport() {
    }

    public static List<SqlLikeLintWarning> warnings(QueryAst ast, Set<String> suppressedCodes) {
        ArrayList<SqlLikeLintWarning> warnings = new ArrayList<>();
        addIfNotSuppressed(warnings,
                suppressedCodes,
                ast.select() != null && ast.select().wildcard(),
                SqlLikeLintCodes.SELECT_WILDCARD,
                "Avoid SELECT * in long-lived SQL-like queries; prefer explicit fields or aliases");
        addIfNotSuppressed(warnings,
                suppressedCodes,
                (ast.limit() != null || ast.offset() != null) && ast.orders().isEmpty(),
                SqlLikeLintCodes.LIMIT_WITHOUT_ORDER,
                "LIMIT/OFFSET without ORDER BY can yield unstable result ordering");
        addIfNotSuppressed(warnings,
                suppressedCodes,
                hasInlineStringLiteral(ast.filters()) || hasInlineStringLiteral(ast.havingFilters()),
                SqlLikeLintCodes.INLINE_STRING_LITERAL,
                "Prefer named parameters over inline string literals for reusable and safer queries");
        return Collections.unmodifiableList(warnings);
    }

    public static List<Map<String, Object>> warningEntries(List<SqlLikeLintWarning> warnings) {
        ArrayList<Map<String, Object>> entries = new ArrayList<>(warnings.size());
        for (SqlLikeLintWarning warning : warnings) {
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", warning.code());
            entry.put("message", warning.message());
            entries.add(Collections.unmodifiableMap(entry));
        }
        return Collections.unmodifiableList(entries);
    }

    private static boolean hasInlineStringLiteral(List<FilterAst> filters) {
        for (FilterAst filter : filters) {
            if (filter.value() instanceof String) {
                return true;
            }
            if (filter.value() instanceof SubqueryValueAst subqueryValueAst) {
                if (hasInlineStringLiteral(subqueryValueAst.query().filters())
                        || hasInlineStringLiteral(subqueryValueAst.query().havingFilters())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addIfNotSuppressed(List<SqlLikeLintWarning> warnings,
                                           Set<String> suppressedCodes,
                                           boolean condition,
                                           String code,
                                           String message) {
        if (condition && !suppressedCodes.contains(code)) {
            warnings.add(new SqlLikeLintWarning(code, message));
        }
    }
}

