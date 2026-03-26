package laughing.man.commits.examples.spring.boot.basic;

import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.CreateEmployeeRequest;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DashboardOptions;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.DashboardPayload;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.EmployeeView;
import laughing.man.commits.examples.spring.boot.basic.EmployeeExampleTypes.RuntimeInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Thin HTTP adapter for the starter example.
 *
 * Read next:
 * - examples/spring-boot-starter-basic/README.md
 * - /docs/entry-points.md
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeQueryController {

    private final EmployeeDashboardService dashboardService;

    public EmployeeQueryController(EmployeeDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public List<EmployeeView> employees() {
        return dashboardService.employees();
    }

    @GetMapping("/departments")
    public List<String> departments() {
        return dashboardService.departments();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeView addEmployee(@RequestBody CreateEmployeeRequest request) {
        return dashboardService.addEmployee(request);
    }

    @GetMapping("/dashboard-options")
    public DashboardOptions dashboardOptions() {
        return dashboardService.dashboardOptions();
    }

    @GetMapping("/dashboard")
    public DashboardPayload dashboard(@RequestParam(name = "statsMode", defaultValue = "PRESET_BY_PAYROLL")
                                      String statsMode,
                                      @RequestParam(name = "chartMode", defaultValue = "PRESET_QUERY")
                                      String chartMode) {
        return dashboardService.dashboard(statsMode, chartMode);
    }

    @GetMapping("/top-paid")
    public List<EmployeeView> topPaid(@RequestParam(name = "department", defaultValue = "Engineering")
                                      String department,
                                      @RequestParam(name = "minSalary", defaultValue = "100000")
                                      int minSalary,
                                      @RequestParam(name = "limit", defaultValue = "3")
                                      int limit) {
        return dashboardService.topPaid(department, minSalary, limit);
    }

    @GetMapping("/runtime")
    public RuntimeInfo runtime() {
        return dashboardService.runtime();
    }
}
