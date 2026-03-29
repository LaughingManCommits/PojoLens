package laughing.man.commits.examples.spring.boot.quickstart;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.examples.spring.boot.quickstart.QuickstartEmployeeTypes.Employee;
import laughing.man.commits.examples.spring.boot.quickstart.QuickstartEmployeeTypes.EmployeeView;
import laughing.man.commits.examples.spring.boot.quickstart.QuickstartEmployeeTypes.RuntimeInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
public class QuickstartEmployeeController {

    private static final String TOP_PAID_QUERY = "select id, name, department, salary "
            + "where salary >= :minSalary "
            + "order by salary desc limit :limit";

    private final PojoLensRuntime pojoLensRuntime;
    private final List<Employee> employees = List.of(
            new Employee(1, "Ava", "Engineering", 145000),
            new Employee(2, "Milan", "Engineering", 132000),
            new Employee(3, "Lina", "Finance", 125000),
            new Employee(4, "Noah", "Support", 87000),
            new Employee(5, "Sara", "Marketing", 98000)
    );

    public QuickstartEmployeeController(PojoLensRuntime pojoLensRuntime) {
        this.pojoLensRuntime = pojoLensRuntime;
    }

    @GetMapping
    public List<Employee> employees() {
        return employees;
    }

    @GetMapping("/top-paid")
    public List<EmployeeView> topPaid(@RequestParam(name = "minSalary", defaultValue = "100000") int minSalary,
                                      @RequestParam(name = "limit", defaultValue = "3") int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 25));
        return pojoLensRuntime
                .parse(TOP_PAID_QUERY)
                .params(Map.of(
                        "minSalary", Math.max(0, minSalary),
                        "limit", cappedLimit
                ))
                .filter(employees, EmployeeView.class);
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
}
