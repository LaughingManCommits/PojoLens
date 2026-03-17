package laughing.man.commits.sqllike;

import laughing.man.commits.sqllike.internal.params.SqlLikeParameterSupport;
import laughing.man.commits.util.StringUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reusable SQL-like template with declared parameter schema.
 */
public final class SqlLikeTemplate {

    private final SqlLikeQuery query;
    private final Set<String> expectedParams;

    private SqlLikeTemplate(SqlLikeQuery query, Set<String> expectedParams) {
        this.query = Objects.requireNonNull(query, "query must not be null");
        this.expectedParams = Collections.unmodifiableSet(new LinkedHashSet<>(expectedParams));
        validateSchemaMatchesQuery(this.query, this.expectedParams);
    }

    public static SqlLikeTemplate of(String sqlLikeQuery, String... expectedParams) {
        return of(SqlLikeQuery.of(sqlLikeQuery), expectedParams);
    }

    public static SqlLikeTemplate of(SqlLikeQuery query, String... expectedParams) {
        return new SqlLikeTemplate(query, normalizeExpected(expectedParams));
    }

    public String source() {
        return query.source();
    }

    public Set<String> expectedParams() {
        return expectedParams;
    }

    public SqlLikeQuery bind(Map<String, ?> parameters) {
        Map<String, Object> normalized = normalize(parameters);
        validateInputSchema(normalized.keySet());
        return query.params(normalized);
    }

    public SqlLikeQuery bind(SqlParams parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        return bind(parameters.asMap());
    }

    private static Set<String> normalizeExpected(String... expectedParams) {
        if (expectedParams == null) {
            throw new IllegalArgumentException("expectedParams must not be null");
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String expectedParam : expectedParams) {
            String normalized = normalizeName(expectedParam, "expected parameter name");
            if (!names.add(normalized)) {
                throw new IllegalArgumentException(
                        "Duplicate SQL-like template parameter schema entry: " + normalized);
            }
        }
        return names;
    }

    private static Map<String, Object> normalize(Map<String, ?> parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : parameters.entrySet()) {
            String key = normalizeName(entry.getKey(), "parameter name");
            normalized.put(key, entry.getValue());
        }
        return normalized;
    }

    private void validateInputSchema(Set<String> provided) {
        TreeSet<String> missing = new TreeSet<>(expectedParams);
        missing.removeAll(provided);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing SQL-like template parameter(s): " + missing);
        }
        TreeSet<String> unknown = new TreeSet<>(provided);
        unknown.removeAll(expectedParams);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown SQL-like template parameter(s): " + unknown);
        }
    }

    private static void validateSchemaMatchesQuery(SqlLikeQuery query, Set<String> expected) {
        Set<String> queryParams = SqlLikeParameterSupport.collectParameterNames(query.ast());
        TreeSet<String> missingInSchema = new TreeSet<>(queryParams);
        missingInSchema.removeAll(expected);
        if (!missingInSchema.isEmpty()) {
            throw new IllegalArgumentException("Template schema missing SQL-like parameter(s): " + missingInSchema);
        }

        TreeSet<String> unknownInSchema = new TreeSet<>(expected);
        unknownInSchema.removeAll(queryParams);
        if (!unknownInSchema.isEmpty()) {
            throw new IllegalArgumentException(
                    "Template schema declares unknown SQL-like parameter(s): " + unknownInSchema);
        }
    }

    private static String normalizeName(String value, String label) {
        if (StringUtil.isNullOrBlank(value)) {
            throw new IllegalArgumentException(label + " must not be null/blank");
        }
        return value;
    }
}

