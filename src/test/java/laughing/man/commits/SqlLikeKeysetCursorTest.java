package laughing.man.commits;

import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeKeysetCursorTest {

    @Test
    public void keysetAfterShouldReturnNextPageForDescendingOrder() {
        SqlLikeCursor cursor = SqlLikeCursor.builder()
                .put("salary", 120000)
                .put("id", 1)
                .build();

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc, id desc limit 20")
                .keysetAfter(cursor)
                .filter(sampleCursorSource(), Employee.class);

        assertEquals(Arrays.asList("Dan", "Bob"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void keysetBeforeShouldReturnPreviousPageForDescendingOrder() {
        SqlLikeCursor cursor = SqlLikeCursor.builder()
                .put("salary", 120000)
                .put("id", 1)
                .build();

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc, id desc limit 20")
                .keysetBefore(cursor)
                .filter(sampleCursorSource(), Employee.class);

        assertEquals(Arrays.asList("Eli", "Cara"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void keysetCursorShouldRoundtripToken() {
        SqlLikeCursor cursor = SqlLikeCursor.builder()
                .put("salary", 120000)
                .put("id", 1)
                .put("active", true)
                .build();

        String token = cursor.toToken();
        SqlLikeCursor decoded = SqlLikeCursor.fromToken(token);

        assertEquals(cursor, decoded);
    }

    @Test
    public void keysetAfterShouldSupportTokenBasedCursor() {
        String token = PojoLens.newKeysetCursorBuilder()
                .put("salary", 120000)
                .put("id", 1)
                .build()
                .toToken();

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc, id desc limit :limit")
                .keysetAfter(PojoLens.parseKeysetCursor(token))
                .params(java.util.Map.of("limit", 20))
                .filter(sampleCursorSource(), Employee.class);

        assertEquals(Arrays.asList("Dan", "Bob"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void keysetAfterShouldRejectQueryWithoutOrderBy() {
        try {
            PojoLens.parse("where active = true limit 20")
                    .keysetAfter(SqlLikeCursor.builder().put("salary", 120000).build());
            fail("Expected ORDER BY validation failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.CURSOR_ORDER_REQUIRED));
        }
    }

    @Test
    public void keysetAfterShouldRejectMissingCursorField() {
        try {
            PojoLens.parse("where active = true order by salary desc, id desc limit 20")
                    .keysetAfter(SqlLikeCursor.builder().put("salary", 120000).build());
            fail("Expected cursor field mismatch");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.CURSOR_FIELD_MISMATCH));
            assertTrue(ex.getMessage().contains("id"));
        }
    }

    @Test
    public void keysetAfterShouldMatchExpectedWindowOnLargeTieHeavyDataset() {
        List<Employee> source = largeTieHeavySource(5000);
        SqlLikeCursor cursor = SqlLikeCursor.builder()
                .put("salary", 102000)
                .put("id", 2500)
                .build();

        List<Employee> rows = PojoLens
                .parse("where active = true order by salary desc, id desc limit 25")
                .keysetAfter(cursor)
                .filter(source, Employee.class);

        List<Integer> actualIds = rows.stream().map(row -> row.id).toList();
        List<Integer> expectedIds = source.stream()
                .filter(row -> row.salary < 102000 || (row.salary == 102000 && row.id < 2500))
                .sorted(Comparator.comparingInt((Employee row) -> row.salary).reversed()
                        .thenComparing(Comparator.comparingInt((Employee row) -> row.id).reversed()))
                .limit(25)
                .map(row -> row.id)
                .toList();

        assertEquals(expectedIds, actualIds);
    }

    private static List<Employee> sampleCursorSource() {
        Date now = new Date();
        return Arrays.asList(
                new Employee(1, "Alice", "Engineering", 120000, now, true),
                new Employee(2, "Bob", "Finance", 90000, now, true),
                new Employee(3, "Cara", "Engineering", 130000, now, true),
                new Employee(4, "Dan", "Engineering", 110000, now, true),
                new Employee(5, "Eli", "Engineering", 130000, now, true)
        );
    }

    private static List<Employee> largeTieHeavySource(int size) {
        Date now = new Date();
        ArrayList<Employee> rows = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            int salary = 100000 + (i % 5) * 1000;
            rows.add(new Employee(i, "e" + i, "Engineering", salary, now, true));
        }
        return rows;
    }
}
