package laughing.man.commits.examples.spring.boot.quickstart;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.examples.spring.boot.quickstart.QuickstartEmployeeTypes.DepartmentSalarySummaryView;
import laughing.man.commits.examples.spring.boot.quickstart.QuickstartEmployeeTypes.Employee;
import laughing.man.commits.examples.spring.boot.quickstart.QuickstartEmployeeTypes.EmployeeView;
import laughing.man.commits.examples.spring.boot.quickstart.QuickstartEmployeeTypes.RuntimeInfo;
import org.springframework.core.env.Environment;
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
    private static final String DEPARTMENT_SALARY_SUMMARY_QUERY =
            "select department, count(*) as headcount, avg(salary) as averageSalary, max(salary) as topSalary "
                    + "where salary >= :minSalary "
                    + "group by department order by topSalary desc";
    private static final String BY_DEPARTMENT_QUERY = "select id, name, department, salary "
            + "where department = :department "
            + "order by salary desc limit :limit";
    private static final String BY_SALARY_RANGE_QUERY = "select id, name, department, salary "
            + "where salary >= :minSalary and salary <= :maxSalary "
            + "order by salary desc limit :limit";

    private final PojoLensRuntime pojoLensRuntime;
    private final boolean virtualThreadsEnabled;
    private final List<Employee> employees = List.of(
            new Employee(1, "Ava", "Engineering", 145000),
            new Employee(2, "Milan", "Engineering", 132000),
            new Employee(3, "Lina", "Finance", 125000),
            new Employee(4, "Noah", "Support", 87000),
            new Employee(5, "Sara", "Marketing", 98000)
    );

    public QuickstartEmployeeController(PojoLensRuntime pojoLensRuntime, Environment environment) {
        this.pojoLensRuntime = pojoLensRuntime;
        this.virtualThreadsEnabled = environment.getProperty("spring.threads.virtual.enabled", Boolean.class, false);
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

    @GetMapping("/by-department")
    public List<EmployeeView> byDepartment(@RequestParam(name = "department") String department,
                                            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 25));
        return pojoLensRuntime
                .parse(BY_DEPARTMENT_QUERY)
                .params(Map.of(
                        "department", department,
                        "limit", cappedLimit
                ))
                .filter(employees, EmployeeView.class);
    }

    @GetMapping("/by-salary-range")
    public List<EmployeeView> bySalaryRange(@RequestParam(name = "minSalary", defaultValue = "0") int minSalary,
                                            @RequestParam(name = "maxSalary", defaultValue = "999999") int maxSalary,
                                            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        int clampedMinSalary = Math.max(0, minSalary);
        int clampedMaxSalary = Math.max(clampedMinSalary, maxSalary);
        int cappedLimit = Math.max(1, Math.min(limit, 25));
        return pojoLensRuntime
                .parse(BY_SALARY_RANGE_QUERY)
                .params(Map.of(
                        "minSalary", clampedMinSalary,
                        "maxSalary", clampedMaxSalary,
                        "limit", cappedLimit
                ))
                .filter(employees, EmployeeView.class);
    }

    @GetMapping("/department-salary-summary")
    public List<DepartmentSalarySummaryView> departmentSalarySummary(
            @RequestParam(name = "minSalary", defaultValue = "0") int minSalary) {
        return pojoLensRuntime
                .parse(DEPARTMENT_SALARY_SUMMARY_QUERY)
                .params(Map.of("minSalary", Math.max(0, minSalary)))
                .filter(employees, DepartmentSalarySummaryView.class);
    }

    @GetMapping("/runtime")
    public RuntimeInfo runtime() {
        return new RuntimeInfo(
                pojoLensRuntime.isStrictParameterTypes(),
                pojoLensRuntime.isLintMode(),
                pojoLensRuntime.sqlLikeCache().isEnabled(),
                pojoLensRuntime.statsPlanCache().isEnabled(),
                virtualThreadsEnabled,
                Thread.currentThread().isVirtual()
        );
    }
}
