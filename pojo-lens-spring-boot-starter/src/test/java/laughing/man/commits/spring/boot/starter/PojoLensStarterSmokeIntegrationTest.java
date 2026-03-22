package laughing.man.commits.spring.boot.starter;

import laughing.man.commits.PojoLensRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = PojoLensStarterSmokeIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "pojo-lens.preset=DEV",
                "pojo-lens.strict-parameter-types=true",
                "pojo-lens.lint-mode=true"
        }
)
class PojoLensStarterSmokeIntegrationTest {

    @Autowired
    private PojoLensRuntime pojoLensRuntime;

    @LocalServerPort
    private int port;

    @Test
    void contextLoadsRuntimeBeanFromStarterAutoConfiguration() {
        assertNotNull(pojoLensRuntime);
        assertTrue(pojoLensRuntime.isStrictParameterTypes());
        assertTrue(pojoLensRuntime.isLintMode());
    }

    @Test
    void topPaidEndpointReturnsOrderedRows() throws Exception {
        String endpoint = "http://localhost:" + port
                + "/api/employees/top-paid?department=Engineering&minSalary=100000&limit=2";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertNotNull(response);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"department\":\"Engineering\""));
        assertTrue(response.body().contains("\"salary\":145000"));
        assertTrue(response.body().contains("\"salary\":126000"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        EmployeeEndpoint employeeEndpoint(PojoLensRuntime pojoLensRuntime) {
            return new EmployeeEndpoint(pojoLensRuntime);
        }
    }

    @RestController
    @RequestMapping("/api/employees")
    static class EmployeeEndpoint {

        private static final List<Employee> EMPLOYEES = List.of(
                new Employee(1L, "Alicia", "Engineering", 145_000),
                new Employee(2L, "Mateo", "Engineering", 126_000),
                new Employee(3L, "Priya", "Engineering", 112_000),
                new Employee(4L, "Elena", "Engineering", 98_000),
                new Employee(5L, "Jordan", "Sales", 119_000)
        );

        private final PojoLensRuntime pojoLensRuntime;

        EmployeeEndpoint(PojoLensRuntime pojoLensRuntime) {
            this.pojoLensRuntime = pojoLensRuntime;
        }

        @GetMapping("/top-paid")
        public List<EmployeeView> topPaid(@RequestParam(name = "department", defaultValue = "Engineering")
                                          String department,
                                          @RequestParam(name = "minSalary", defaultValue = "100000")
                                          int minSalary,
                                          @RequestParam(name = "limit", defaultValue = "3")
                                          int limit) {
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
    }

    public static final class Employee {
        public long id;
        public String name;
        public String department;
        public int salary;

        public Employee(long id, String name, String department, int salary) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.salary = salary;
        }
    }

    public static final class EmployeeView {
        public long id;
        public String name;
        public String department;
        public int salary;

        public EmployeeView() {
        }
    }
}
