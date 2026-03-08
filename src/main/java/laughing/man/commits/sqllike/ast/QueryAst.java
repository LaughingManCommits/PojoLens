package laughing.man.commits.sqllike.ast;

import java.util.List;

public final class QueryAst {

    private final SelectAst select;
    private final List<JoinAst> joins;
    private final List<FilterAst> filters;
    private final FilterExpressionAst whereExpression;
    private final List<String> groupByFields;
    private final List<FilterAst> havingFilters;
    private final FilterExpressionAst havingExpression;
    private final List<OrderAst> orders;
    private final Integer limit;

    public QueryAst(SelectAst select,
                    List<JoinAst> joins,
                    List<FilterAst> filters,
                    FilterExpressionAst whereExpression,
                    List<String> groupByFields,
                    List<FilterAst> havingFilters,
                    FilterExpressionAst havingExpression,
                    List<OrderAst> orders,
                    Integer limit) {
        this.select = select;
        this.joins = List.copyOf(joins);
        this.filters = List.copyOf(filters);
        this.whereExpression = whereExpression;
        this.groupByFields = List.copyOf(groupByFields);
        this.havingFilters = List.copyOf(havingFilters);
        this.havingExpression = havingExpression;
        this.orders = List.copyOf(orders);
        this.limit = limit;
    }

    public SelectAst select() {
        return select;
    }

    public List<JoinAst> joins() {
        return joins;
    }

    public JoinAst join() {
        return joins.isEmpty() ? null : joins.get(0);
    }

    public List<FilterAst> filters() {
        return filters;
    }

    public FilterExpressionAst whereExpression() {
        return whereExpression;
    }

    public List<String> groupByFields() {
        return groupByFields;
    }

    public List<FilterAst> havingFilters() {
        return havingFilters;
    }

    public FilterExpressionAst havingExpression() {
        return havingExpression;
    }

    public List<OrderAst> orders() {
        return orders;
    }

    public Integer limit() {
        return limit;
    }

    public boolean hasJoins() {
        return !joins.isEmpty();
    }

    public boolean hasAggregation() {
        if (select == null) {
            return false;
        }
        for (SelectFieldAst field : select.fields()) {
            if (field.metricField()) {
                return true;
            }
        }
        return false;
    }
}

