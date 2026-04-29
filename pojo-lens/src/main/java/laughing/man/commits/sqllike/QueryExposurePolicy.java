package laughing.man.commits.sqllike;

import laughing.man.commits.util.StringUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Allowlist policy for user-authored query fields and named sources.
 * <p>
 * Empty allowlists are unrestricted for that dimension.
 */
public final class QueryExposurePolicy {

    private static final QueryExposurePolicy UNRESTRICTED = builder().build();

    private final Set<String> allowedFields;
    private final Set<String> allowedSources;

    private QueryExposurePolicy(Builder builder) {
        this.allowedFields = Collections.unmodifiableSet(new LinkedHashSet<>(builder.allowedFields));
        this.allowedSources = Collections.unmodifiableSet(new LinkedHashSet<>(builder.allowedSources));
    }

    /**
     * Returns an unrestricted policy.
     *
     * @return unrestricted policy
     */
    public static QueryExposurePolicy unrestricted() {
        return UNRESTRICTED;
    }

    /**
     * Starts a policy builder.
     *
     * @return policy builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts a builder from this policy.
     *
     * @return builder containing this policy's allowlists
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Returns allowed field names. Empty means fields are unrestricted.
     *
     * @return allowed field names
     */
    public Set<String> allowedFields() {
        return allowedFields;
    }

    /**
     * Returns allowed named sources. Empty means sources are unrestricted.
     *
     * @return allowed source names
     */
    public Set<String> allowedSources() {
        return allowedSources;
    }

    /**
     * Returns true when field references are allowlist-checked.
     *
     * @return true when field allowlist is non-empty
     */
    public boolean restrictsFields() {
        return !allowedFields.isEmpty();
    }

    /**
     * Returns true when named sources are allowlist-checked.
     *
     * @return true when source allowlist is non-empty
     */
    public boolean restrictsSources() {
        return !allowedSources.isEmpty();
    }

    /**
     * Returns true when the field is allowed or fields are unrestricted.
     *
     * @param field field name to check
     * @return true when allowed
     */
    public boolean allowsField(String field) {
        return !restrictsFields() || allowedFields.contains(field);
    }

    /**
     * Returns true when the source is allowed or sources are unrestricted.
     *
     * @param source source name to check
     * @return true when allowed
     */
    public boolean allowsSource(String source) {
        return !restrictsSources() || allowedSources.contains(source);
    }

    /**
     * Builder for {@link QueryExposurePolicy}.
     */
    public static final class Builder {
        private final LinkedHashSet<String> allowedFields = new LinkedHashSet<>();
        private final LinkedHashSet<String> allowedSources = new LinkedHashSet<>();

        private Builder() {
        }

        private Builder(QueryExposurePolicy policy) {
            this.allowedFields.addAll(policy.allowedFields);
            this.allowedSources.addAll(policy.allowedSources);
        }

        /**
         * Adds allowed field names.
         *
         * @param fields field names
         * @return this builder
         */
        public Builder allowFields(String... fields) {
            addAll(allowedFields, "field", fields);
            return this;
        }

        /**
         * Adds allowed named sources.
         *
         * @param sources source names
         * @return this builder
         */
        public Builder allowSources(String... sources) {
            addAll(allowedSources, "source", sources);
            return this;
        }

        /**
         * Builds the policy.
         *
         * @return query exposure policy
         */
        public QueryExposurePolicy build() {
            return new QueryExposurePolicy(this);
        }

        private static void addAll(Set<String> target, String label, String... values) {
            Objects.requireNonNull(values, label + " names must not be null");
            for (String value : values) {
                if (StringUtil.isNullOrBlank(value)) {
                    throw new IllegalArgumentException(label + " name must not be null/blank");
                }
                target.add(value.trim());
            }
        }
    }
}
