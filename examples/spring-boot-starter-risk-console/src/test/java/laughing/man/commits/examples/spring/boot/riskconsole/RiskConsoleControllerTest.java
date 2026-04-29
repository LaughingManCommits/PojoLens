package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RiskConsoleApplication.class)
@ActiveProfiles("test")
class RiskConsoleControllerTest {

    @Autowired
    private RiskConsoleDashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RiskConsoleController(dashboardService)).build();
    }

    @Test
    void transactionsEndpointSupportsSortParams() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("range", "30d")
                        .param("sortBy", "amount")
                        .param("sortDirection", "asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns").isArray())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.loadedRows").isNumber())
                .andExpect(jsonPath("$.totalRows").isNumber())
                .andExpect(jsonPath("$.pageSize").value(25))
                .andExpect(jsonPath("$.rows[0].amount").isNumber());
    }

    @Test
    void reportInspectEndpointReturnsReviewMetadata() throws Exception {
        mockMvc.perform(get("/api/reports/regional-declines/inspect")
                        .param("range", "30d")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value("regional-declines"))
                .andExpect(jsonPath("$.queryText").value(org.hamcrest.Matchers.containsString("merchantRegion")))
                .andExpect(jsonPath("$.defaultParams.status").value("DECLINED"))
                .andExpect(jsonPath("$.diagnostics.valid").value(true))
                .andExpect(jsonPath("$.pushdownPreview.mode").exists())
                .andExpect(jsonPath("$.explain").isMap());
    }

    @Test
    void valueStoryEndpointReturnsLivePojoLensStory() throws Exception {
        mockMvc.perform(get("/api/dashboard/value-story")
                        .param("range", "30d")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Why PojoLens matters here"))
                .andExpect(jsonPath("$.metrics").isArray())
                .andExpect(jsonPath("$.metrics[0].label").value("Filtered snapshot"))
                .andExpect(jsonPath("$.featureStrip[0]").exists());
    }

    @Test
    void reviewQueueEndpointRespectsRiskBandFilter() throws Exception {
        mockMvc.perform(get("/api/reviews/queue")
                        .param("range", "30d")
                        .param("riskBand", "HIGH")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows[0].riskBand").value("HIGH"));
    }

    @Test
    void workbenchEndpointReturnsRichPojoLensMetadata() throws Exception {
        mockMvc.perform(get("/api/dashboard/workbench")
                        .param("range", "30d")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskTierStats.rows").isArray())
                .andExpect(jsonPath("$.analystWorkload.rows").isArray())
                .andExpect(jsonPath("$.computedFields[0]").exists())
                .andExpect(jsonPath("$.executionGuard.maxRowsScanned").value(250000))
                .andExpect(jsonPath("$.joinExplain.joinSourceBindings").isMap());
    }

    @Test
    void queryStudioEndpointReturnsNaturalTypedAndCancellationPayloads() throws Exception {
        mockMvc.perform(get("/api/dashboard/query-studio")
                        .param("range", "30d")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naturalQuery.queryText").value(org.hamcrest.Matchers.containsString("review state")))
                .andExpect(jsonPath("$.naturalQuery.rows").isArray())
                .andExpect(jsonPath("$.typedQuery.rows").isArray())
                .andExpect(jsonPath("$.cancellationDemo.cancelled").value(true))
                .andExpect(jsonPath("$.cancellationDemo.blockCode").value("GUARD_CANCELLED"))
                .andExpect(jsonPath("$.cancellationDemo.executionGuard.maxRowsReturned").value(-1));
    }

    @Test
    void naturalSavedReportInspectEndpointWorks() throws Exception {
        mockMvc.perform(get("/api/reports/open-reviews-by-region-natural/inspect")
                        .param("range", "30d")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value("open-reviews-by-region-natural"))
                .andExpect(jsonPath("$.queryText").value(org.hamcrest.Matchers.containsString("merchantRegion")))
                .andExpect(jsonPath("$.diagnostics.valid").value(true))
                .andExpect(jsonPath("$.explain.equivalentSqlLike").exists());
    }

    @Test
    void transactionsEndpointRejectsBadSortField() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("range", "30d")
                        .param("sortBy", "merchantRegion")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transactionsEndpointRejectsBadCursor() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("range", "30d")
                        .param("cursor", "bad-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
