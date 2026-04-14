package laughing.man.commits.builder;

import laughing.man.commits.enums.Clauses;

import java.util.List;
import java.util.function.Consumer;

public final class QueryRule {

    private final String column;
    private final Object value;
    private final Clauses clause;
    private final String dateFormat;
    private final FluentSubqueryPredicate subqueryPredicate;

    private QueryRule(String column, Object value, Clauses clause, String dateFormat) {
        this(column, value, clause, dateFormat, null);
    }

    private QueryRule(String column,
                      Object value,
                      Clauses clause,
                      String dateFormat,
                      FluentSubqueryPredicate subqueryPredicate) {
        this.column = column;
        this.value = value;
        this.clause = clause;
        this.dateFormat = dateFormat;
        this.subqueryPredicate = subqueryPredicate;
    }

    public static QueryRule of(String column, Object value, Clauses clause) {
        return new QueryRule(column, value, clause, null);
    }

    public static QueryRule of(String column, Object value, Clauses clause, String dateFormat) {
        return new QueryRule(column, value, clause, dateFormat);
    }

    public static <T, R> QueryRule of(FieldSelector<T, R> selector, Object value, Clauses clause) {
        return new QueryRule(FieldSelectors.resolve(selector), value, clause, null);
    }

    public static <T, R> QueryRule of(FieldSelector<T, R> selector, Object value, Clauses clause, String dateFormat) {
        return new QueryRule(FieldSelectors.resolve(selector), value, clause, dateFormat);
    }

    public static QueryRule inSubquery(String column,
                                       String subqueryOutputField,
                                       Consumer<QueryBuilder> subqueryConfigurer) {
        return new QueryRule(
                column,
                null,
                Clauses.IN,
                null,
                FluentSubqueryPredicate.in(column, subqueryOutputField, subqueryConfigurer)
        );
    }

    public static <T, R> QueryRule inSubquery(FieldSelector<T, R> selector,
                                              String subqueryOutputField,
                                              Consumer<QueryBuilder> subqueryConfigurer) {
        return inSubquery(FieldSelectors.resolve(selector), subqueryOutputField, subqueryConfigurer);
    }

    public static QueryRule inSubquery(String column,
                                       List<?> subqueryRows,
                                       String subqueryOutputField,
                                       Consumer<QueryBuilder> subqueryConfigurer) {
        return new QueryRule(
                column,
                null,
                Clauses.IN,
                null,
                FluentSubqueryPredicate.in(column, subqueryRows, subqueryOutputField, subqueryConfigurer)
        );
    }

    public static <T, R> QueryRule inSubquery(FieldSelector<T, R> selector,
                                              List<?> subqueryRows,
                                              String subqueryOutputField,
                                              Consumer<QueryBuilder> subqueryConfigurer) {
        return inSubquery(FieldSelectors.resolve(selector), subqueryRows, subqueryOutputField, subqueryConfigurer);
    }

    public static QueryRule exists(Consumer<QueryBuilder> subqueryConfigurer) {
        return new QueryRule(null, null, null, null, FluentSubqueryPredicate.exists(subqueryConfigurer, false));
    }

    public static QueryRule exists(List<?> subqueryRows, Consumer<QueryBuilder> subqueryConfigurer) {
        return new QueryRule(null, null, null, null, FluentSubqueryPredicate.exists(subqueryRows, subqueryConfigurer, false));
    }

    public static QueryRule notExists(Consumer<QueryBuilder> subqueryConfigurer) {
        return new QueryRule(null, null, null, null, FluentSubqueryPredicate.exists(subqueryConfigurer, true));
    }

    public static QueryRule notExists(List<?> subqueryRows, Consumer<QueryBuilder> subqueryConfigurer) {
        return new QueryRule(null, null, null, null, FluentSubqueryPredicate.exists(subqueryRows, subqueryConfigurer, true));
    }

    public String getColumn() {
        return column;
    }

    public Object getValue() {
        return value;
    }

    public Clauses getClause() {
        return clause;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    boolean hasSubqueryPredicate() {
        return subqueryPredicate != null;
    }

    FluentSubqueryPredicate getSubqueryPredicate() {
        return subqueryPredicate;
    }
}

