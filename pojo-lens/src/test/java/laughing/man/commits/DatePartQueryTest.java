package laughing.man.commits;

import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.table.TabularSchema;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-29 slice 2: date-part functions in SQL-like queries.
 */
class DatePartQueryTest {

    @Test
    void datePartsFilterRows() {
        List<Employee> rows = employees();

        assertEquals(List.of("Alice", "Bob"), sql("where year(hireDate) = 2024 order by name", rows));
        assertEquals(List.of("Bob", "Cara"), sql("where month(hireDate) in (6, 7, 8) order by name", rows));
        assertEquals(List.of("Cara"), sql("where quarter(hireDate) = 3 and year(hireDate) = 2025", rows));
        assertEquals(List.of("Bob"), sql("where day_of_week(hireDate) >= 6", rows));
        assertEquals(List.of("Dan"), sql("where hour(hireDate) = 23 and minute(hireDate) = 30", rows));
    }

    @Test
    void zoneArgumentMovesInstantsIntoLocalTime() {
        List<Employee> rows = employees();

        assertEquals(List.of("Dan"), sql("where day(hireDate) = 31", rows));
        assertEquals(List.of("Dan"), sql("where day(hireDate, 'Asia/Tokyo') = 1 and year(hireDate, 'Asia/Tokyo') = 2026", rows));
    }

    @Test
    void nullDatesNeverMatchAValue() {
        List<Employee> rows = employees();

        assertEquals(List.of("Eve"), sql("where year(hireDate) is null", rows));
        assertEquals(List.of("Alice", "Bob", "Cara", "Dan"), sql("where year(hireDate) > 1900 order by name", rows));
    }

    @Test
    void selectedDatePartsAreIntegers() {
        List<HireParts> parts = PojoLensSql
                .parse("select name, year(hireDate) as hireYear, quarter(hireDate) as hireQuarter where name = 'Cara'")
                .filter(employees(), HireParts.class);
        TabularSchema schema = PojoLensSql.parse("select year(hireDate) as hireYear").schema(QueryRow.class);

        assertEquals(2025, parts.get(0).hireYear);
        assertEquals(3, parts.get(0).hireQuarter);
        assertEquals(Integer.class, schema.column("hireYear").type());
    }

    @Test
    void registryDatePartsGroupRows() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("hireYear", "year(hireDate)", Integer.class)
                .build();

        List<YearCount> counts = PojoLensSql
                .parse("select hireYear, count(*) as total where hireYear is not null group by hireYear order by hireYear")
                .computedFields(registry)
                .filter(employees(), YearCount.class);

        assertEquals(List.of(2024, 2025), counts.stream().map(row -> row.hireYear).toList());
        assertEquals(List.of(2L, 2L), counts.stream().map(row -> row.total).toList());
    }

    @Test
    void validationRejectsBadDatePartArguments() {
        assertTrue(validationError("where year(name) = 2024").contains("Function YEAR needs a date/time value"));
        assertTrue(validationError("where year(hireDate, 'Nowhere/City') = 2024").contains("Unsupported time zone"));
        assertTrue(validationError("where year(hireDate, department) = 2024").contains("zone must be a text literal"));
        assertTrue(validationError("where month(hireDate) contains '1'")
                .contains("only support text operators on text expressions"));

        IllegalArgumentException strict = assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("where year(hireDate) = :year")
                        .strictParameterTypes()
                        .params(Map.of("year", "2024"))
                        .filter(employees(), Employee.class));
        assertTrue(strict.getMessage().contains(SqlLikeErrorCodes.PARAM_TYPE_MISMATCH), strict::getMessage);
    }

    private static List<Employee> employees() {
        return List.of(
                new Employee(1, "Alice", "Engineering", 120000, date("2024-01-15T09:00:00Z"), true),
                new Employee(2, "Bob", "Finance", 90000, date("2024-06-15T09:00:00Z"), true),
                new Employee(3, "Cara", "Engineering", 130000, date("2025-08-04T09:00:00Z"), true),
                new Employee(4, "Dan", "Engineering", 110000, date("2025-12-31T23:30:00Z"), false),
                new Employee(5, "Eve", "Finance", 100000, null, true));
    }

    private static Date date(String instant) {
        return Date.from(Instant.parse(instant));
    }

    private static List<String> sql(String query, List<Employee> rows) {
        return PojoLensSql.parse(query).filter(rows, Employee.class).stream().map(row -> row.name).toList();
    }

    private static String validationError(String query) {
        return assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse(query).filter(employees(), QueryRow.class)).getMessage();
    }

    static class HireParts {
        String name;
        Integer hireYear;
        Integer hireQuarter;

        HireParts() {
        }
    }

    static class YearCount {
        Integer hireYear;
        long total;

        YearCount() {
        }
    }
}
