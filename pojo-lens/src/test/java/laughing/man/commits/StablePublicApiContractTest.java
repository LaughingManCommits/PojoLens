package laughing.man.commits;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.enums.WindowFunction;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.sqllike.SqlLikeBoundQuery;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlLikeTemplate;
import laughing.man.commits.sqllike.SqlParams;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StablePublicApiContractTest {

    @Test
    public void stableEntryPointFactoryMethodsShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(PojoLens.class, "newQueryBuilder", List.class);
        requirePublicStaticMethod(PojoLens.class, "parse", String.class);
        requirePublicStaticMethod(PojoLens.class, "template", String.class, String[].class);
        requirePublicStaticMethod(PojoLens.class, "newRuntime");
        requirePublicStaticMethod(PojoLens.class, "newRuntime", PojoLensRuntimePreset.class);
        requirePublicStaticMethod(PojoLens.class, "newKeysetCursorBuilder");
        requirePublicStaticMethod(PojoLens.class, "parseKeysetCursor", String.class);
        requirePublicStaticMethod(PojoLens.class, "toChartData", List.class, ChartSpec.class);

        requirePublicStaticMethod(PojoLensCore.class, "newQueryBuilder", List.class);
        requirePublicStaticMethod(PojoLensSql.class, "parse", String.class);
        requirePublicStaticMethod(PojoLensSql.class, "template", String.class, String[].class);
    }

    @Test
    public void stableQueryBuilderAndFilterMethodsShouldRemainAvailable() throws Exception {
        requirePublicMethod(QueryBuilder.class, "addRule", String.class, Object.class, Clauses.class);
        requirePublicMethod(QueryBuilder.class, "addOrder", String.class);
        requirePublicMethod(QueryBuilder.class, "addGroup", String.class);
        requirePublicMethod(QueryBuilder.class, "addField", String.class);
        requirePublicMethod(QueryBuilder.class, "addMetric", String.class, Metric.class, String.class);
        requirePublicMethod(QueryBuilder.class, "addCount", String.class);
        requirePublicMethod(QueryBuilder.class, "addWindow", String.class, WindowFunction.class, List.class, List.class);
        requirePublicMethod(QueryBuilder.class, "addWindow", String.class, WindowFunction.class, String.class, boolean.class, List.class, List.class);
        requirePublicMethod(QueryBuilder.class, "addHaving", String.class, Object.class, Clauses.class);
        requirePublicMethod(QueryBuilder.class, "addQualify", String.class, Object.class, Clauses.class);
        requirePublicMethod(QueryBuilder.class, "addJoinBeans", String.class, List.class, String.class, Join.class);
        requirePublicMethod(QueryBuilder.class, "limit", int.class);
        requirePublicMethod(QueryBuilder.class, "offset", int.class);
        requirePublicMethod(QueryBuilder.class, "initFilter");
        requirePublicMethod(QueryBuilder.class, "explain");
        requirePublicMethod(QueryBuilder.class, "schema", Class.class);

        requirePublicMethod(Filter.class, "filter", Class.class);
        requirePublicMethod(Filter.class, "iterator", Class.class);
        requirePublicMethod(Filter.class, "stream", Class.class);
        requirePublicMethod(Filter.class, "chart", Class.class, ChartSpec.class);
        requirePublicMethod(Filter.class, "join");
    }

    @Test
    public void stableSqlLikeContractsShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(SqlLikeQuery.class, "of", String.class);
        requirePublicMethod(SqlLikeQuery.class, "source");
        requirePublicMethod(SqlLikeQuery.class, "params", Map.class);
        requirePublicMethod(SqlLikeQuery.class, "params", SqlParams.class);
        requirePublicMethod(SqlLikeQuery.class, "keysetAfter", SqlLikeCursor.class);
        requirePublicMethod(SqlLikeQuery.class, "keysetBefore", SqlLikeCursor.class);
        requirePublicMethod(SqlLikeQuery.class, "bindTyped", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "filter", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "iterator", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "stream", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "chart", List.class, Class.class, ChartSpec.class);
        requirePublicMethod(SqlLikeQuery.class, "schema", Class.class);
        requirePublicMethod(SqlLikeQuery.class, "explain", List.class, Class.class);
        requirePublicMethod(SqlLikeQuery.class, "sort");

        requirePublicMethod(SqlLikeBoundQuery.class, "filter");
        requirePublicMethod(SqlLikeBoundQuery.class, "iterator");
        requirePublicMethod(SqlLikeBoundQuery.class, "stream");
        requirePublicMethod(SqlLikeBoundQuery.class, "chart", ChartSpec.class);

        requirePublicStaticMethod(SqlLikeTemplate.class, "of", String.class, String[].class);
        requirePublicMethod(SqlLikeTemplate.class, "bind", Map.class);
        requirePublicMethod(SqlLikeTemplate.class, "bind", SqlParams.class);
        requirePublicMethod(SqlLikeTemplate.class, "source");
        requirePublicMethod(SqlLikeTemplate.class, "expectedParams");

        requirePublicStaticMethod(SqlParams.class, "builder");
        requirePublicStaticMethod(SqlParams.class, "empty");
        requirePublicMethod(SqlParams.class, "asMap");
    }

    @Test
    public void stableFluentAndSqlLikeFlowsShouldExecute() {
        List<Employee> fluentRows = PojoLens.newQueryBuilder(sampleEmployees())
                .addRule("active", true, Clauses.EQUAL)
                .addOrder("salary")
                .limit(2)
                .initFilter()
                .filter(Sort.DESC, Employee.class);

        assertEquals(List.of("Cara", "Alice"), fluentRows.stream().map(row -> row.name).toList());

        List<Employee> sqlRows = PojoLens
                .parse("where active = true order by salary desc limit 2")
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Cara", "Alice"), sqlRows.stream().map(row -> row.name).toList());
    }

    @Test
    public void stableRuntimeTemplateAndCursorFlowsShouldExecute() {
        PojoLensRuntime runtime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);
        assertTrue(runtime.isStrictParameterTypes());
        assertTrue(runtime.isLintMode());

        SqlLikeTemplate template = runtime.template(
                "where department = :dept and active = :active",
                "dept",
                "active"
        );
        List<Employee> rows = template
                .bind(SqlParams.builder().put("dept", "Engineering").put("active", true).build())
                .filter(sampleEmployees(), Employee.class);
        assertEquals(2, rows.size());

        SqlLikeCursor cursor = PojoLens.newKeysetCursorBuilder().put("salary", 120000).put("id", 1).build();
        String token = cursor.toToken();
        SqlLikeCursor decoded = PojoLens.parseKeysetCursor(token);
        assertNotNull(decoded);
        assertEquals(cursor, decoded);
    }

    private static Method requirePublicMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                () -> "Expected public method: " + type.getSimpleName() + "." + name);
        return method;
    }

    private static Method requirePublicStaticMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = requirePublicMethod(type, name, parameterTypes);
        assertTrue(Modifier.isStatic(method.getModifiers()),
                () -> "Expected static method: " + type.getSimpleName() + "." + name);
        return method;
    }
}
