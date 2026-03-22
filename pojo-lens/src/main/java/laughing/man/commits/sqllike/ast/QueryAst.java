package laughing.man.commits.sqllike.ast;

import laughing.man.commits.util.StringUtil;

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
    private final String limitParameter;
    private final Integer offset;
    private final String offsetParameter;

    public QueryAst(SelectAst select,
                    List<JoinAst> joins,
                    List<FilterAst> filters,
                    FilterExpressionAst whereExpression,
                    List<String> groupByFields,
                    List<FilterAst> havingFilters,
                    FilterExpressionAst havingExpression,
                    List<OrderAst> orders,
                    Integer limit,
                    Integer offset) {
        this(select, joins, filters, whereExpression, groupByFields, havingFilters, havingExpression, orders, limit, null, offset, null);
    }

    public QueryAst(SelectAst select,
                    List<JoinAst> joins,
                    List<FilterAst> filters,
                    FilterExpressionAst whereExpression,
                    List<String> groupByFields,
                    List<FilterAst> havingFilters,
                    FilterExpressionAst havingExpression,
                    List<OrderAst> orders,
                    Integer limit,
                    String limitParameter,
                    Integer offset,
                    String offsetParameter) {
        this.select = select;
        this.joins = List.copyOf(joins);
        this.filters = List.copyOf(filters);
        this.whereExpression = whereExpression;
        this.groupByFields = List.copyOf(groupByFields);
        this.havingFilters = List.copyOf(havingFilters);
        this.havingExpression = havingExpression;
        this.orders = List.copyOf(orders);
        this.limit = limit;
        this.limitParameter = limitParameter;
        this.offset = offset;
        this.offsetParameter = offsetParameter;
        validatePaginationState("LIMIT", limit, limitParameter);
        validatePaginationState("OFFSET", offset, offsetParameter);
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

    public String limitParameter() {
        return limitParameter;
    }

    public Integer offset() {
        return offset;
    }

    public String offsetParameter() {
        return offsetParameter;
    }

    public boolean hasLimitClause() {
        return limit != null || limitParameter != null;
    }

    public boolean hasOffsetClause() {
        return offset != null || offsetParameter != null;
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

    private static void validatePaginationState(String clause, Integer value, String parameter) {
        if (value != null && parameter != null) {
            throw new IllegalArgumentException(clause + " cannot declare both literal and parameter value");
        }
        if (parameter != null && StringUtil.isNullOrBlank(parameter)) {
            throw new IllegalArgumentException(clause + " parameter name must not be null/blank");
        }
    }
}

