package laughing.man.commits.natural;

import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Immutable runtime-scoped vocabulary for natural query field aliases.
 */
public final class NaturalVocabulary {

    private static final NaturalVocabulary EMPTY = new NaturalVocabulary(Map.of());

    private final Map<String, List<String>> aliasesByNormalizedPhrase;

    private NaturalVocabulary(Map<String, List<String>> aliasesByNormalizedPhrase) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : aliasesByNormalizedPhrase.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.aliasesByNormalizedPhrase = Collections.unmodifiableMap(copy);
    }

    public static NaturalVocabulary empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return aliasesByNormalizedPhrase.isEmpty();
    }

    public List<String> resolveAliasTargets(String normalizedPhrase) {
        if (StringUtil.isNullOrBlank(normalizedPhrase)) {
            return List.of();
        }
        List<String> matches = aliasesByNormalizedPhrase.get(normalizedPhrase);
        return matches == null ? List.of() : matches;
    }

    public Map<String, List<String>> aliases() {
        return aliasesByNormalizedPhrase;
    }

    public static final class Builder {
        private final LinkedHashMap<String, LinkedHashSet<String>> aliasesByPhrase = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder field(String fieldName, String... aliases) {
            if (StringUtil.isNullOrBlank(fieldName)) {
                throw new IllegalArgumentException("fieldName must not be null/blank");
            }
            if (aliases == null || aliases.length == 0) {
                return this;
            }
            String normalizedFieldName = NaturalVocabularySupport.normalizeNaturalFieldToken(fieldName);
            for (String alias : aliases) {
                String normalizedAlias = NaturalVocabularySupport.normalizeNaturalFieldToken(alias);
                aliasesByPhrase.computeIfAbsent(normalizedAlias, ignored -> new LinkedHashSet<>()).add(normalizedFieldName);
            }
            return this;
        }

        public NaturalVocabulary build() {
            if (aliasesByPhrase.isEmpty()) {
                return EMPTY;
            }
            LinkedHashMap<String, List<String>> built = new LinkedHashMap<>();
            for (Map.Entry<String, LinkedHashSet<String>> entry : aliasesByPhrase.entrySet()) {
                built.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return new NaturalVocabulary(built);
        }
    }
}
