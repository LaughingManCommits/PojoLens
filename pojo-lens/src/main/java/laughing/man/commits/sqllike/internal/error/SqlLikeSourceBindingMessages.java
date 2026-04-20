package laughing.man.commits.sqllike.internal.error;

import laughing.man.commits.internal.NameSuggestions;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared source-binding validation messages for SQL-like paths.
 */
public final class SqlLikeSourceBindingMessages {

    private SqlLikeSourceBindingMessages() {
    }

    public static String missingJoinSourceBinding(String source, Set<String> candidateSources) {
        return missingSourceBinding("JOIN", source, candidateSources);
    }

    public static String missingSubquerySourceBinding(String source, Set<String> candidateSources) {
        return missingSourceBinding("subquery", source, candidateSources);
    }

    private static String missingSourceBinding(String kind, String source, Set<String> candidateSources) {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(candidateSources, "candidateSources must not be null");
        return "Missing " + kind + " source binding for '" + source + "'"
                + NameSuggestions.formatFragment(NameSuggestions.suggest(source, candidateSources))
                + availableSourceBindingsFragment(candidateSources);
    }

    private static String availableSourceBindingsFragment(Set<String> candidateSources) {
        return candidateSources.isEmpty() ? "" : " Available source binding(s): " + new TreeSet<>(candidateSources);
    }
}

