package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.PojoLensRuntime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
public class EmployeeQueryController {

    private static final List<Employee> EMPLOYEES = List.of(
            new Employee(1L, "Alicia", "Engineering", 145_000),
            new Employee(2L, "Mateo", "Engineering", 126_000),
            new Employee(3L, "Priya", "Engineering", 112_000),
            new Employee(4L, "Elena", "Engineering", 98_000),
            new Employee(5L, "Jordan", "Sales", 119_000),
            new Employee(6L, "Noah", "Sales", 106_000),
            new Employee(7L, "Ava", "Marketing", 101_000)
    );

    private final PojoLensRuntime pojoLensRuntime;

    public EmployeeQueryController(PojoLensRuntime pojoLensRuntime) {
        this.pojoLensRuntime = pojoLensRuntime;
    }

    @GetMapping("/top-paid")
    public List<EmployeeView> topPaid(@RequestParam(defaultValue = "Engineering") String department,
                                      @RequestParam(defaultValue = "100000") int minSalary,
                                      @RequestParam(defaultValue = "3") int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 25));
        return pojoLensRuntime
                .parse("select id, name, department, salary "
                        + "where department = :department and salary >= :minSalary "
                        + "order by salary desc limit :limit")
                .params(Map.of(
                        "department", department,
                        "minSalary", minSalary,
                        "limit", cappedLimit
                ))
                .filter(EMPLOYEES, EmployeeView.class);
    }

    @GetMapping("/runtime")
    public RuntimeInfo runtime() {
        return new RuntimeInfo(
                pojoLensRuntime.isStrictParameterTypes(),
                pojoLensRuntime.isLintMode(),
                pojoLensRuntime.sqlLikeCache().isEnabled(),
                pojoLensRuntime.statsPlanCache().isEnabled()
        );
    }

    private record Employee(long id, String name, String department, int salary) {
    }

    public record EmployeeView(long id, String name, String department, int salary) {
    }

    public record RuntimeInfo(boolean strictParameterTypes,
                              boolean lintMode,
                              boolean sqlLikeCacheEnabled,
                              boolean statsPlanCacheEnabled) {
    }
}
