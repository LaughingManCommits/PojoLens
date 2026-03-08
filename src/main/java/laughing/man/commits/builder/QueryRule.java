package laughing.man.commits.builder;

import laughing.man.commits.enums.Clauses;

public final class QueryRule {

    private final String column;
    private final Object value;
    private final Clauses clause;
    private final String dateFormat;

    private QueryRule(String column, Object value, Clauses clause, String dateFormat) {
        this.column = column;
        this.value = value;
        this.clause = clause;
        this.dateFormat = dateFormat;
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
}

