package laughing.man.commits.sqllike.internal.explain;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.ast.FilterAst;
import laughing.man.commits.sqllike.ast.OrderAst;
import laughing.man.commits.sqllike.ast.ParameterValueAst;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;
import laughing.man.commits.sqllike.ast.SubqueryValueAst;
import laughing.man.commits.filter.FilterExecutionPlanCache;
import laughing.man.commits.sqllike.internal.lint.SqlLikeLintSupport;
import laughing.man.commits.sqllike.internal.cache.DefaultSqlLikeQueryCacheSupport;
import laughing.man.commits.sqllike.internal.params.BoundParameterValue;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator;
import laughing.man.commits.util.StringUtil;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic SQL-like explain payload helpers.
 */
public final class SqlLikeExplainSupport {

    private SqlLikeExplainSupport() {
    }

    public static Map<String, Object> payload(String source,
                                              QueryAst ast,
                                              Map<String, List<?>> joinSources,
                                              Map<String, Object> stageRowCounts,
                                              boolean lintMode,
                                              List<SqlLikeLintWarning> lintWarnings,
                                              ComputedFieldRegistry computedFieldRegistry) {
        LinkedHashMap<String, Object> explain = new LinkedHashMap<>();
        explain.put("type", "sql-like");
        explain.put("source", source);
        explain.put("normalizedQuery", source);
        explain.put("hasJoin", ast.hasJoins());
        explain.put("whereRuleCount", ast.filters().size());
        explain.put("havingRuleCount", ast.havingFilters().size());
        explain.put("qualifyRuleCount", ast.qualifyFilters().size());
        explain.put("groupBy", new ArrayList<>(ast.groupByFields()));
        explain.put("orderBy", orderEntries(ast));
        explain.put("resolvedSortDirection", resolvedSortDirection(ast));
        explain.put("offset", ast.offset());
        explain.put("limit", ast.limit());
        explain.put("select", selectEntries(ast));
        explain.put("projectionMode", projectionMode(ast));
        explain.put("metrics", metricEntries(ast));
        explain.put("timeBuckets", timeBucketEntries(ast));
        explain.put("computedFields", ComputedFieldSupport.usedExplainEntries(computedFieldRegistry, usedComputedFieldNames(ast, computedFieldRegistry)));
        explain.put("joinSourceBindings", joinSourceBindings(ast, joinSources));
        explain.put("parameterSnapshot", parameterSnapshot(ast));
        if (lintMode) {
            explain.put("lintWarnings", SqlLikeLintSupport.warningEntries(lintWarnings));
        }
        explain.put("sqlLikeCache", DefaultSqlLikeQueryCacheSupport.snapshot());
        explain.put("statsPlanCache", FilterExecutionPlanCache.snapshot());
        explain.put("stageRowCounts", stageRowCounts);
        return Collections.unmodifiableMap(explain);
    }

    public static String resolvedSortDirection(QueryAst ast) {
        if (ast.orders().isEmpty()) {
            return null;
        }
        Sort first = ast.orders().get(0).sort();
        for (OrderAst order : ast.orders()) {
            if (order.sort() != first) {
                return "MIXED";
            }
        }
        return first.name();
    }

    public static String projectionMode(QueryAst ast) {
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            return "direct";
        }
        for (SelectFieldAst field : select.fields()) {
            if (field.aliased()
                    || field.computedField()
                    || field.metricField()
                    || field.timeBucketField()
                    || field.windowField()) {
                return "alias/computed";
            }
        }
        if (ast.hasAggregation() || !ast.groupByFields().isEmpty()) {
            return "alias/computed";
        }
        return "direct";
    }

    public static Map<String, String> joinSourceBindings(QueryAst ast, Map<String, List<?>> joinSources) {
        if (!ast.hasJoins()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        ast.joins().forEach(join ->
                bindings.put(join.childSource(), joinSources.containsKey(join.childSource()) ? "bound" : "unbound"));
        return Collections.unmodifiableMap(bindings);
    }

    public static Map<String, Object> parameterSnapshot(QueryAst ast) {
        TreeMap<String, Map<String, Object>> snapshot = new TreeMap<>();
        collectParameterSnapshots(ast.filters(), snapshot);
        collectParameterSnapshots(ast.havingFilters(), snapshot);
        collectParameterSnapshots(ast.qualifyFilters(), snapshot);
        collectPaginationParameterSnapshots(ast, snapshot);
        return Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
    }

    private static void collectParameterSnapshots(List<FilterAst> filters,
                                                  Map<String, Map<String, Object>> snapshot) {
        for (FilterAst filter : filters) {
            Object value = filter.value();
            if (value instanceof ParameterValueAst parameterValueAst) {
                snapshot.putIfAbsent(parameterValueAst.name(), unresolvedParameter());
            } else if (value instanceof BoundParameterValue boundParameterValue) {
                snapshot.putIfAbsent(boundParameterValue.name(), boundParameter(boundParameterValue.value()));
            } else if (value instanceof SubqueryValueAst subqueryValueAst) {
                collectParameterSnapshots(subqueryValueAst.query().filters(), snapshot);
                collectParameterSnapshots(subqueryValueAst.query().havingFilters(), snapshot);
                collectPaginationParameterSnapshots(subqueryValueAst.query(), snapshot);
            }
        }
    }

    private static void collectPaginationParameterSnapshots(QueryAst ast,
                                                            Map<String, Map<String, Object>> snapshot) {
        if (ast.limitParameter() != null) {
            snapshot.putIfAbsent(ast.limitParameter(), unresolvedParameter());
        }
        if (ast.offsetParameter() != null) {
            snapshot.putIfAbsent(ast.offsetParameter(), unresolvedParameter());
        }
    }

    private static Map<String, Object> unresolvedParameter() {
        LinkedHashMap<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("status", "unresolved");
        return Collections.unmodifiableMap(parameter);
    }

    private static Map<String, Object> boundParameter(Object value) {
        LinkedHashMap<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("status", "bound");
        parameter.put("shape", parameterShape(value));
        parameter.put("type", parameterType(value));
        if (value instanceof Map<?, ?> map) {
            parameter.put("size", map.size());
        } else if (value instanceof Iterable<?> iterable) {
            parameter.put("size", iterableSize(iterable));
        } else if (value != null && value.getClass().isArray()) {
            parameter.put("size", Array.getLength(value));
        }
        parameter.put("redacted", Boolean.TRUE);
        return Collections.unmodifiableMap(parameter);
    }

    private static String parameterShape(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "map";
        }
        if (value instanceof Iterable<?>) {
            return "iterable";
        }
        if (value.getClass().isArray()) {
            return "array";
        }
        return "scalar";
    }

    private static String parameterType(Object value) {
        if (value == null) {
            return null;
        }
        return value.getClass().getSimpleName();
    }

    private static int iterableSize(Iterable<?> iterable) {
        int size = 0;
        for (Object ignored : iterable) {
            size++;
        }
        return size;
    }

    private static List<String> selectEntries(QueryAst ast) {
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            return Collections.singletonList("*");
        }
        ArrayList<String> entries = new ArrayList<>();
        for (SelectFieldAst field : select.fields()) {
            entries.add(field.field() + "->" + field.outputName());
        }
        return entries;
    }

    private static List<String> metricEntries(QueryAst ast) {
        SelectAst select = ast.select();
        if (select == null) {
            return Collections.emptyList();
        }
        ArrayList<String> entries = new ArrayList<>();
        for (SelectFieldAst field : select.fields()) {
            if (!field.metricField()) {
                continue;
            }
            String sourceField = field.countAll() ? "*" : field.field();
            entries.add(field.metric() + ":" + sourceField + ":" + field.outputName());
        }
        return entries;
    }

    private static List<String> timeBucketEntries(QueryAst ast) {
        SelectAst select = ast.select();
        if (select == null) {
            return Collections.emptyList();
        }
        ArrayList<String> entries = new ArrayList<>();
        for (SelectFieldAst field : select.fields()) {
            if (!field.timeBucketField()) {
                continue;
            }
            entries.add(field.outputName() + ":" + field.field() + ":" + field.timeBucketPreset().explainToken());
        }
        return entries;
    }

    private static List<String> orderEntries(QueryAst ast) {
        ArrayList<String> entries = new ArrayList<>();
        for (OrderAst order : ast.orders()) {
            entries.add(order.field() + ":" + order.sort());
        }
        return entries;
    }

    private static Set<String> usedComputedFieldNames(QueryAst ast, ComputedFieldRegistry registry) {
        if (registry == null || registry.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> used = new LinkedHashSet<>();
        SelectAst select = ast.select();
        if (select != null && !select.wildcard()) {
            for (SelectFieldAst field : select.fields()) {
                collectComputedNames(field.field(), registry, used);
            }
        }
        for (FilterAst filter : ast.filters()) {
            collectComputedNames(filter.field(), registry, used);
        }
        for (FilterAst filter : ast.havingFilters()) {
            collectComputedNames(filter.field(), registry, used);
        }
        for (String groupByField : ast.groupByFields()) {
            collectComputedNames(groupByField, registry, used);
        }
        for (OrderAst order : ast.orders()) {
            collectComputedNames(order.field(), registry, used);
        }
        return used;
    }

    private static void collectComputedNames(String value, ComputedFieldRegistry registry, Set<String> used) {
        if (StringUtil.isNullOrBlank(value)) {
            return;
        }
        if (registry.contains(value)) {
            used.add(value);
        }
        try {
            for (String identifier : SqlExpressionEvaluator.collectIdentifiers(value)) {
                if (registry.contains(identifier)) {
                    used.add(identifier);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Non-expression references such as aggregate functions are not
            // part of computed-field explain extraction.
        }
    }
}
