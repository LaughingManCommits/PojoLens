package laughing.man.commits.sqllike.ast;

import laughing.man.commits.util.StringUtil;

import java.util.Objects;

/**
 * Named SQL-like parameter placeholder value (e.g. :minSalary).
 */
public final class ParameterValueAst {

    private final String name;

    public ParameterValueAst(String name) {
        if (StringUtil.isNullOrBlank(name)) {
            throw new IllegalArgumentException("parameter name must not be null/blank");
        }
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParameterValueAst)) {
            return false;
        }
        ParameterValueAst that = (ParameterValueAst) other;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return ":" + name;
    }
}

