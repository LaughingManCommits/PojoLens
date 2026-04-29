package laughing.man.commits.sqllike.internal.error;

import laughing.man.commits.internal.NameSuggestions;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared field-resolution validation messages for SQL-like paths.
 */
public final class SqlLikeFieldMessages {

    private SqlLikeFieldMessages() {
    }

    public static String unknownField(String field, String clauseName, Set<String> allowedFields) {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(clauseName, "clauseName must not be null");
        Objects.requireNonNull(allowedFields, "allowedFields must not be null");
        return baseUnknownField(field, clauseName, allowedFields)
                + " Allowed fields: " + new TreeSet<>(allowedFields);
    }

    public static String unknownFieldIfAllowedKnown(String field, String clauseName, Set<String> allowedFields) {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(clauseName, "clauseName must not be null");
        Objects.requireNonNull(allowedFields, "allowedFields must not be null");
        return baseUnknownField(field, clauseName, allowedFields)
                + allowedFieldsFragment(allowedFields);
    }

    public static String allowedFieldsFragment(Set<String> allowedFields) {
        Objects.requireNonNull(allowedFields, "allowedFields must not be null");
        return allowedFields.isEmpty() ? "" : " Allowed fields: " + new TreeSet<>(allowedFields);
    }

    public static String allowedSourceFieldsFragment(Set<String> sourceFields) {
        Objects.requireNonNull(sourceFields, "sourceFields must not be null");
        return sourceFields.isEmpty() ? "" : " Allowed source fields: " + new TreeSet<>(sourceFields);
    }

    private static String baseUnknownField(String field, String clauseName, Set<String> allowedFields) {
        return "Unknown field '" + field + "' in " + clauseName + " clause."
                + NameSuggestions.formatFragment(NameSuggestions.suggest(field, allowedFields));
    }
}
