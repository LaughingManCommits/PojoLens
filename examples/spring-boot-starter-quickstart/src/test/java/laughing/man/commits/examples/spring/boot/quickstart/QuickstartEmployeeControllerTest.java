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
    void runtimeEndpointExposesStarterFlags() throws Exception {
        HttpResponse<String> response = get("/api/employees/runtime");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.get("strictParameterTypes").isBoolean()).isTrue();
        assertThat(body.get("lintMode").isBoolean()).isTrue();
        assertThat(body.get("sqlLikeCacheEnabled").isBoolean()).isTrue();
        assertThat(body.get("statsPlanCacheEnabled").isBoolean()).isTrue();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
