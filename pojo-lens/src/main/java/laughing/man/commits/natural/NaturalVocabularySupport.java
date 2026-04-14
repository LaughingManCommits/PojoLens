package laughing.man.commits.natural;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NaturalVocabularySupport {

    private NaturalVocabularySupport() {
    }

    public static String normalizeNaturalFieldToken(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Identifier phrase must not be blank");
        }
        if (trimmed.contains(".")) {
            String[] parts = trimmed.split("\\.");
            ArrayList<String> normalized = new ArrayList<>(parts.length);
            for (String part : parts) {
                normalized.add(normalizeIdentifierPhrase(part));
            }
            return String.join(".", normalized);
        }
        return normalizeIdentifierPhrase(trimmed);
    }

    private static String normalizeIdentifierPhrase(String raw) {
        if (raw.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return raw;
        }
        String cleaned = raw.replace('-', ' ').replace('/', ' ');
        String[] parts = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].replaceAll("[^A-Za-z0-9_]", "");
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() == 0) {
                sb.append(part.substring(0, 1).toLowerCase(Locale.ROOT));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            } else {
                sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        if (sb.length() == 0) {
            throw new IllegalArgumentException("Identifier phrase must contain letters or digits");
        }
        return sb.toString();
    }

    public static List<String> filterAllowedTargets(List<String> candidateTargets, java.util.Set<String> allowedFields) {
        if (candidateTargets == null || candidateTargets.isEmpty() || allowedFields == null || allowedFields.isEmpty()) {
            return List.of();
        }
        ArrayList<String> resolved = new ArrayList<>(candidateTargets.size());
        for (String candidateTarget : candidateTargets) {
            if (allowedFields.contains(candidateTarget)) {
                resolved.add(candidateTarget);
            }
        }
        return List.copyOf(resolved);
    }
}
