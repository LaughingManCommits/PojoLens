package laughing.man.commits;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamingExecutionOutputTest {

    @Test
    public void fluentStreamShouldSupportLazySimpleFilterWithOffsetAndLimit() {
        QueryBuilder builder = PojoLens.newQueryBuilder(sampleEmployees())
                .addRule("active", true, Clauses.EQUAL)
                .offset(1)
                .limit(2);

        List<String> names = builder.initFilter()
                .stream(Employee.class)
                .map(row -> row.name)
                .toList();

        assertEquals(List.of("Bob", "Cara"), names);
    }

    @Test
    public void fluentIteratorShouldExposeRows() {
        QueryBuilder builder = PojoLens.newQueryBuilder(sampleEmployees())
                .addRule("active", true, Clauses.EQUAL)
                .limit(1);

        Iterator<Employee> iterator = builder.initFilter().iterator(Employee.class);
        assertTrue(iterator.hasNext());
        assertEquals("Alice", iterator.next().name);
        assertFalse(iterator.hasNext());
    }

    @Test
    public void sqlLikeStreamShouldExposeRows() {
        List<String> names = PojoLens
                .parse("where active = true limit 2")
                .stream(sampleEmployees(), Employee.class)
                .map(row -> row.name)
                .toList();

        assertEquals(List.of("Alice", "Bob"), names);
    }

    @Test
    public void sqlLikeBoundStreamShouldExposeRows() {
        SqlLikeQuery query = PojoLens.parse("where active = true limit 2");
        List<String> names = query.bindTyped(sampleEmployees(), Employee.class)
                .stream()
                .map(row -> row.name)
                .toList();

        assertEquals(List.of("Alice", "Bob"), names);
    }

    @Test
    public void sqlLikeStreamShouldFallbackForOrderedQueries() {
        List<String> names = PojoLens
                .parse("where active = true order by salary desc limit 2")
                .stream(sampleEmployees(), Employee.class)
                .map(row -> row.name)
                .toList();

        assertEquals(List.of("Cara", "Alice"), names);
    }
}

