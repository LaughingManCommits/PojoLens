package laughing.man.commits.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class NameSuggestions {

    private static final int MAX_SUGGESTIONS = 3;
    private static final int DISTANCE_THRESHOLD = 2;

    private NameSuggestions() {
    }

    public static List<String> suggest(String unknown, Collection<String> candidates) {
        if (unknown == null || unknown.isBlank() || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedUnknown = normalize(unknown);
        List<Ranked> ranked = new ArrayList<>();
        for (String candidate : candidates) {
            String normalizedCandidate = normalize(candidate);
            int distance = levenshteinDistance(normalizedUnknown, normalizedCandidate);
            boolean prefixMatch = normalizedCandidate.startsWith(normalizedUnknown)
                    || normalizedUnknown.startsWith(normalizedCandidate);
            if (distance <= DISTANCE_THRESHOLD || prefixMatch) {
                ranked.add(new Ranked(candidate, distance));
            }
        }
        ranked.sort(Comparator.comparingInt(Ranked::distance).thenComparing(Ranked::name));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < ranked.size() && i < MAX_SUGGESTIONS; i++) {
            result.add(ranked.get(i).name());
        }
        return result;
    }

    public static String formatFragment(List<String> suggestions) {
        if (suggestions.isEmpty()) {
            return "";
        }
        if (suggestions.size() == 1) {
            return " Did you mean '" + suggestions.get(0) + "'?";
        }
        return " Did you mean one of " + suggestions + "?";
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static int levenshteinDistance(String left, String right) {
        int leftLength = left.length();
        int rightLength = right.length();
        if (leftLength == 0) {
            return rightLength;
        }
        if (rightLength == 0) {
            return leftLength;
        }
        int[] previous = new int[rightLength + 1];
        int[] current = new int[rightLength + 1];
        for (int j = 0; j <= rightLength; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= leftLength; i++) {
            current[0] = i;
            char leftChar = left.charAt(i - 1);
            for (int j = 1; j <= rightLength; j++) {
                int cost = leftChar == right.charAt(j - 1) ? 0 : 1;
                int deletion = previous[j] + 1;
                int insertion = current[j - 1] + 1;
                int substitution = previous[j - 1] + cost;
                current[j] = Math.min(Math.min(deletion, insertion), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[rightLength];
    }

    private static final class Ranked {
        private final String name;
        private final int distance;

        private Ranked(String name, int distance) {
            this.name = name;
            this.distance = distance;
        }

        private String name() {
            return name;
        }

        private int distance() {
            return distance;
        }
    }
}
