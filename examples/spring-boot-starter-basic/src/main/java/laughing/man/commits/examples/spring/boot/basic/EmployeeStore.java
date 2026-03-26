package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.Employee;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps the example self-contained with an in-memory data source.
 *
 * Read next:
 * - examples/spring-boot-starter-basic/README.md
 */
@Component
class EmployeeStore {

    private static final List<Employee> SEED_EMPLOYEES = List.of(
            new Employee(1L, "Alicia", "Engineering", 145_000),
            new Employee(2L, "Mateo", "Engineering", 126_000),
            new Employee(3L, "Priya", "Engineering", 112_000),
            new Employee(4L, "Elena", "Engineering", 98_000),
            new Employee(5L, "Jordan", "Sales", 119_000),
            new Employee(6L, "Noah", "Sales", 106_000),
            new Employee(7L, "Ava", "Marketing", 101_000)
    );

    private final List<Employee> employees = new CopyOnWriteArrayList<>(SEED_EMPLOYEES);
    private final AtomicLong nextEmployeeId = new AtomicLong(maxSeedId());

    List<Employee> snapshot() {
        return new ArrayList<>(employees);
    }

    List<String> departments() {
        return snapshot().stream()
                .map(employee -> employee.department)
                .distinct()
                .sorted()
                .toList();
    }

    Employee add(String name, String department, int salary) {
        Employee added = new Employee(nextEmployeeId.incrementAndGet(), name, department, salary);
        employees.add(added);
        return added;
    }

    private static long maxSeedId() {
        return SEED_EMPLOYEES.stream()
                .map(employee -> employee.id)
                .max(Comparator.naturalOrder())
                .orElse(0L);
    }
}
