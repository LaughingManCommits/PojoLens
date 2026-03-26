package laughing.man.commits.sqllike.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SelectAst {

    private final boolean wildcard;
    private final List<SelectFieldAst> fields;
    private final String sourceName;

    public SelectAst(boolean wildcard, List<SelectFieldAst> fields, String sourceName) {
        this.wildcard = wildcard;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
        this.sourceName = sourceName;
    }

    public boolean wildcard() {
        return wildcard;
    }

    public List<SelectFieldAst> fields() {
        return fields;
    }

    public String sourceName() {
        return sourceName;
    }

    public boolean hasAliases() {
        for (SelectFieldAst field : fields) {
            if (field.aliased()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasComputedFields() {
        for (SelectFieldAst field : fields) {
            if (field.computedField()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasWindowFields() {
        for (SelectFieldAst field : fields) {
            if (field.windowField()) {
                return true;
            }
        }
        return false;
    }
}

