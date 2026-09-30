package laughing.man.commits.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Structural preview of a typed predicate tree.
 */
public final class TypedPlanPredicate {

    private final TypedPredicate.Operator operator;
    private final String field;
    private final Object value;
    private final List<Object> values;
    private final String subqueryOutputField;
    private final boolean subqueryExplicitSource;
    private final TypedPlanPreview subqueryPreview;
    private final List<TypedPlanPredicate> children;

    public TypedPlanPredicate(TypedPredicate.Operator operator,
                              String field,
                              Object value,
                              List<Object> values,
                              String subqueryOutputField,
                              boolean subqueryExplicitSource,
                              TypedPlanPreview subqueryPreview,
                              List<TypedPlanPredicate> children) {
        this.operator = Objects.requireNonNull(operator, "operator must not be null");
        this.field = field;
        this.value = value;
        this.values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        this.subqueryOutputField = subqueryOutputField;
        this.subqueryExplicitSource = subqueryExplicitSource;
        this.subqueryPreview = subqueryPreview;
        this.children = List.copyOf(new ArrayList<>(Objects.requireNonNull(children, "children must not be null")));
    }

    public TypedPredicate.Operator operator() {
        return operator;
    }

    public String field() {
        return field;
    }

    public Object value() {
        return value;
    }

    public List<Object> values() {
        return values;
    }

    public String subqueryOutputField() {
        return subqueryOutputField;
    }

    public boolean subqueryExplicitSource() {
        return subqueryExplicitSource;
    }

    public TypedPlanPreview subqueryPreview() {
        return subqueryPreview;
    }

    public List<TypedPlanPredicate> children() {
        return children;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }
}
