package laughing.man.commits.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class FluentSubqueryPredicate {

    enum Type {
        IN,
        EXISTS
    }

    private final Type type;
    private final String targetField;
    private final String outputField;
    private final List<?> sourceRows;
    private final boolean explicitSource;
    private final boolean negated;
    private final Consumer<QueryBuilder> configurer;

    private FluentSubqueryPredicate(Type type,
                                    String targetField,
                                    String outputField,
                                    List<?> sourceRows,
                                    boolean explicitSource,
                                    boolean negated,
                                    Consumer<QueryBuilder> configurer) {
        this.type = type;
        this.targetField = targetField;
        this.outputField = outputField;
        this.sourceRows = sourceRows == null || sourceRows.isEmpty() ? List.of() : new ArrayList<>(sourceRows);
        this.explicitSource = explicitSource;
        this.negated = negated;
        this.configurer = configurer;
    }

    static FluentSubqueryPredicate in(String targetField,
                                      String outputField,
                                      Consumer<QueryBuilder> configurer) {
        return new FluentSubqueryPredicate(Type.IN, targetField, outputField, List.of(), false, false, configurer);
    }

    static FluentSubqueryPredicate in(String targetField,
                                      List<?> sourceRows,
                                      String outputField,
                                      Consumer<QueryBuilder> configurer) {
        return new FluentSubqueryPredicate(Type.IN, targetField, outputField, sourceRows, true, false, configurer);
    }

    static FluentSubqueryPredicate exists(Consumer<QueryBuilder> configurer, boolean negated) {
        return new FluentSubqueryPredicate(Type.EXISTS, null, null, List.of(), false, negated, configurer);
    }

    static FluentSubqueryPredicate exists(List<?> sourceRows,
                                          Consumer<QueryBuilder> configurer,
                                          boolean negated) {
        return new FluentSubqueryPredicate(Type.EXISTS, null, null, sourceRows, true, negated, configurer);
    }

    Type type() {
        return type;
    }

    String targetField() {
        return targetField;
    }

    String outputField() {
        return outputField;
    }

    List<?> sourceRows() {
        return sourceRows;
    }

    boolean explicitSource() {
        return explicitSource;
    }

    boolean negated() {
        return negated;
    }

    Consumer<QueryBuilder> configurer() {
        return configurer;
    }
}
