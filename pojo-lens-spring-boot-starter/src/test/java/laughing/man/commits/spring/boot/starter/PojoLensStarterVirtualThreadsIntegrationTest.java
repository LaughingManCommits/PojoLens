package laughing.man.commits.spring.boot.starter;

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

@SpringBootTest(
        classes = PojoLensStarterSmokeIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "pojo-lens.preset=DEV",
                "pojo-lens.strict-parameter-types=true",
                "pojo-lens.lint-mode=true",
                "spring.threads.virtual.enabled=true"
        }
)
class PojoLensStarterVirtualThreadsIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Test
    void runtimeEndpointConfirmsVirtualRequestHandling() throws Exception {
        HttpResponse<String> response = get("/api/employees/runtime");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body.get("virtualThreadsEnabled").asBoolean()).isTrue();
        assertThat(body.get("requestThreadVirtual").asBoolean()).isTrue();
    }

    @Test
    void topPaidEndpointRemainsFunctionalInVirtualMode() throws Exception {
        HttpResponse<String> response = get(
                "/api/employees/top-paid?department=Engineering&minSalary=100000&limit=2"
        );

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(2);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
