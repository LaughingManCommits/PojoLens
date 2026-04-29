package laughing.man.commits.examples.spring.boot.basic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = BasicExampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("virtual")
class BasicVirtualThreadsIntegrationTest {

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
        HttpResponse<String> response = get("/api/employees/top-paid?department=Engineering&minSalary=100000&limit=2");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(response.body());
        assertThat(body).isNotNull();
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isBetween(1, 2);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
