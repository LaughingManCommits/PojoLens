package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;
import laughing.man.commits.telemetry.QueryTelemetryEvent;
import laughing.man.commits.telemetry.QueryTelemetryStage;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikePushdownAdapterTest {

    @Test
    void resultSetAdapterReadsColumnLabelsIntoRows() {
        ResultSet resultSet = resultSet(
                List.of("id", "name", "department", "salary", "hireDate", "active"),
                Collections.singletonList(row(1, "Alice", "Engineering", 120000, new Date(1L), true))
        );

        List<Employee> rows = SqlLikeResultSetAdapter.read(resultSet, Employee.class);

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).id);
        assertEquals("Alice", rows.get(0).name);
        assertEquals(120000, rows.get(0).salary);
        assertTrue(rows.get(0).active);
    }

    @Test
    void resultSetAdapterRejectsUnknownColumnLabels() {
        ResultSet resultSet = resultSet(
                List.of("id", "unknownColumn"),
                Collections.singletonList(row(1, "value"))
        );

        SqlLikePushdownException ex = assertThrows(SqlLikePushdownException.class,
                () -> SqlLikeResultSetAdapter.read(resultSet, Employee.class));

        assertTrue(ex.getMessage().contains("unknownColumn"));
    }

    @Test
    void filterWithPushdownSplitsHostRowsAndInMemoryAggregation() {
        List<Employee> sourceRows = sampleEmployees();
        SqlLikePushdownAdapter adapter = new SqlLikePushdownAdapter() {
            @Override
            public <T> SqlLikePushdownResult<T> fetch(SqlLikePushdownRequest request, Class<T> rowClass) {
                assertEquals(SqlLikePushdownMode.SPLIT, request.preview().mode());
                assertTrue(request.requestsStage("WHERE"));
                assertSame(Employee.class, rowClass);
                List<T> activeRows = sourceRows.stream()
                        .filter(row -> row.active)
                        .map(rowClass::cast)
                        .toList();
                return SqlLikePushdownResult.of(
                        activeRows,
                        request.requestedStages(),
                        sourceRows.size(),
                        Map.of("adapter", "test")
                );
            }
        };

        List<DepartmentTotal> rows = PojoLensSql
                .parse("select department, count(*) as total where active = true "
                        + "group by department order by total desc")
                .filterWithPushdown(adapter, Employee.class, DepartmentTotal.class);

        assertEquals(2, rows.size());
        assertEquals("Engineering", rows.get(0).department);
        assertEquals(2L, rows.get(0).total);
        assertEquals("Finance", rows.get(1).department);
        assertEquals(1L, rows.get(1).total);
    }

    @Test
    void filterWithPushdownEmitsTelemetry() {
        List<QueryTelemetryEvent> events = new ArrayList<>();
        SqlLikePushdownAdapter adapter = new SqlLikePushdownAdapter() {
            @Override
            public <T> SqlLikePushdownResult<T> fetch(SqlLikePushdownRequest request, Class<T> rowClass) {
                return SqlLikePushdownResult.of(
                        List.of(rowClass.cast(new Employee(3, "Cara", "Engineering", 130000, new Date(1L), true))),
                        request.requestedStages(),
                        4,
                        Map.of("adapter", "test")
                );
            }
        };

        PojoLensSql
                .parse("where active = true order by salary desc limit 1")
                .telemetry(events::add)
                .filterWithPushdown(adapter, Employee.class);

        QueryTelemetryEvent pushdown = events.stream()
                .filter(event -> event.stage() == QueryTelemetryStage.PUSHDOWN)
                .findFirst()
                .orElseThrow();

        assertEquals("FULL", pushdown.metadata().get("mode"));
        assertEquals(List.of("WHERE", "ORDER_BY", "LIMIT"), pushdown.metadata().get("requestedStages"));
        assertEquals(List.of("WHERE", "ORDER_BY", "LIMIT"), pushdown.metadata().get("pushedStages"));
        assertEquals(4, pushdown.rowCountBefore());
        assertEquals(1, pushdown.rowCountAfter());
    }

    private static Object[] row(Object... values) {
        return values;
    }

    private static ResultSet resultSet(List<String> labels, List<Object[]> rows) {
        InvocationHandler handler = new ResultSetHandler(labels, rows);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                handler
        );
    }

    private static ResultSetMetaData metadata(List<String> labels) {
        InvocationHandler handler = (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getColumnCount" -> labels.size();
                case "getColumnLabel", "getColumnName" -> labels.get(((Integer) args[0]) - 1);
                case "toString" -> "ResultSetMetaData" + labels;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        };
        return (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class},
                handler
        );
    }

    public static class DepartmentTotal {
        public String department;
        public long total;

        public DepartmentTotal() {
        }
    }

    private static final class ResultSetHandler implements InvocationHandler {

        private final ResultSetMetaData metadata;
        private final List<Object[]> rows;
        private int index = -1;

        private ResultSetHandler(List<String> labels, List<Object[]> rows) {
            this.metadata = metadata(labels);
            this.rows = rows.stream().map(values -> {
                LinkedHashMap<Integer, Object> checked = new LinkedHashMap<>();
                for (int i = 0; i < values.length; i++) {
                    checked.put(i, values[i]);
                }
                return checked.values().toArray();
            }).toList();
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "next" -> {
                    index++;
                    yield index < rows.size();
                }
                case "getMetaData" -> metadata;
                case "getObject" -> rows.get(index)[((Integer) args[0]) - 1];
                case "toString" -> "ResultSet" + rows;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
