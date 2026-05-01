package laughing.man.commits.examples.spring.boot.quickstart;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuickstartEmployeeControllerTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Test
    void topPaidReturnsSortedAndLimitedRows() throws Exception {
        HttpResponse<String> response = get("/api/employees/top-paid?minSalary=100000&limit=2");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get(0).get("salary").asInt()).isGreaterThanOrEqualTo(body.get(1).get("salary").asInt());
    }

    @Test
    void byDepartmentFiltersExactDepartmentAndSortsBySalaryDesc() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-department?department=Engineering&limit=2");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get(0).get("department").asText()).isEqualTo("Engineering");
        assertThat(body.get(1).get("department").asText()).isEqualTo("Engineering");
        assertThat(body.get(0).get("salary").asInt()).isGreaterThanOrEqualTo(body.get(1).get("salary").asInt());
    }

    @Test
    void byDepartmentUsesDefaultLimitOfTen() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-department?department=Engineering");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(2);
    }

    @Test
    void byDepartmentCapLimitAt25() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-department?department=Engineering&limit=100");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(2);
    }

    @Test
    void runtimeEndpointExposesStarterFlags() throws Exception {
        HttpResponse<String> response = get("/api/employees/runtime");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.get("strictParameterTypes").isBoolean()).isTrue();
        assertThat(body.get("lintMode").isBoolean()).isTrue();
        assertThat(body.get("sqlLikeCacheEnabled").isBoolean()).isTrue();
        assertThat(body.get("statsPlanCacheEnabled").isBoolean()).isTrue();
        assertThat(body.get("virtualThreadsEnabled").asBoolean()).isFalse();
        assertThat(body.get("requestThreadVirtual").asBoolean()).isFalse();
    }

    @Test
    void departmentSalarySummaryAggregatesByDepartment() throws Exception {
        HttpResponse<String> response = get("/api/employees/department-salary-summary?minSalary=90000");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.get(0).get("department").asText()).isEqualTo("Engineering");
        assertThat(body.get(0).get("headcount").asInt()).isEqualTo(2);
        assertThat(body.get(0).get("topSalary").asInt()).isEqualTo(145000);
        assertThat(body.get(0).get("averageSalary").asDouble()).isEqualTo(138500.0d);
        assertThat(body.get(0).get("topSalary").asInt()).isGreaterThanOrEqualTo(body.get(1).get("topSalary").asInt());
    }

    @Test
    void bySalaryRangeFiltersInclusiveRange() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-salary-range?minSalary=100000&maxSalary=140000&limit=5");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get(0).get("name").asText()).isEqualTo("Milan");
        assertThat(body.get(0).get("salary").asInt()).isEqualTo(132000);
        assertThat(body.get(1).get("name").asText()).isEqualTo("Lina");
        assertThat(body.get(1).get("salary").asInt()).isEqualTo(125000);
    }

    @Test
    void bySalaryRangeOrdersBySalaryDesc() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-salary-range?minSalary=90000&maxSalary=150000&limit=5");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThan(1);
        for (int i = 0; i < body.size() - 1; i++) {
            int current = body.get(i).get("salary").asInt();
            int next = body.get(i + 1).get("salary").asInt();
            assertThat(current).isGreaterThanOrEqualTo(next);
        }
    }

    @Test
    void bySalaryRangeUsesDefaultLimitOfTen() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-salary-range?minSalary=0&maxSalary=200000");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(5);
    }

    @Test
    void bySalaryRangeCapLimitAt25() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-salary-range?minSalary=0&maxSalary=200000&limit=100");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(5);
    }

    @Test
    void bySalaryRangeNormalizesMaxSalaryWhenBelowMinSalary() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-salary-range?minSalary=125000&maxSalary=100000&limit=5");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("name").asText()).isEqualTo("Lina");
        assertThat(body.get(0).get("salary").asInt()).isEqualTo(125000);
    }

    @Test
    void bySalaryRangeClampsMinSalaryToZero() throws Exception {
        HttpResponse<String> response = get("/api/employees/by-salary-range?minSalary=-50000&maxSalary=150000&limit=10");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(5);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
