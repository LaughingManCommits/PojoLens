package laughing.man.commits.sqllike.internal.params;

import laughing.man.commits.internal.TextPattern;
import laughing.man.commits.util.StringUtil;

import java.util.Objects;

/**
 * Unbound named parameter whose bound value is a literal (for example a prefix) or a
 * {@code LIKE} pattern, not a regex. Natural {@code starts with :p} / {@code ends with :p}
 * phrases and SQL-like {@code LIKE :p} produce it for a {@code MATCHES} filter; binding
 * keeps the raw value and execution lowers it through the {@link TextPattern}.
 */
public final class PatternParameterValue {

    private final String name;
    private final TextPattern pattern;

    public PatternParameterValue(String name, TextPattern pattern) {
        if (StringUtil.isNullOrBlank(name)) {
            throw new IllegalArgumentException("parameter name must not be null/blank");
        }
        this.name = name;
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
    }

    public String name() {
        return name;
    }

    public TextPattern pattern() {
        return pattern;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PatternParameterValue that
                && name.equals(that.name)
                && pattern.equals(that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, pattern);
    }

    @Override
    public String toString() {
        return ":" + name;
    }
}
