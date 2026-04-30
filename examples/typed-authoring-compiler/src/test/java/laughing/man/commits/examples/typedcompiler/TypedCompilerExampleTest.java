package laughing.man.commits.examples.typedcompiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypedCompilerExampleTest {

    @Test
    void generatedTypedFieldsShouldDriveTypedQuery() {
        List<Employee> rows = TypedCompilerExample.activeEngineering(List.of(
                new Employee("Alice", "Engineering", 120000, true),
                new Employee("Bob", "Finance", 90000, true),
                new Employee("Cara", "Engineering", 130000, true),
                new Employee("Dan", "Engineering", 110000, false)
        ));

        assertEquals(List.of("Cara", "Alice"), rows.stream().map(row -> row.name).toList());
    }
}
