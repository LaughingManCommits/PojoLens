package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.BootstrapPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.MerchantOverviewPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportCatalogItem;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportInspectPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportRunPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReviewQueuePayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.QueryStudioPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.SummaryPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TopMerchantsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionDetailPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TrendsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ValueStoryPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.WorkbenchPayload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RiskConsoleController {

    private final RiskConsoleDashboardService dashboardService;

    public RiskConsoleController(RiskConsoleDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/bootstrap")
    public BootstrapPayload bootstrap() {
        return dashboardService.bootstrap();
    }

    @GetMapping("/dashboard/summary")
    public SummaryPayload summary(@RequestParam(name = "range", defaultValue = "30d") String range,
                                  @RequestParam(name = "region", defaultValue = "ALL") String region,
                                  @RequestParam(name = "status", defaultValue = "ALL") String status,
                                  @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                  @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                  @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.summary(range, region, status, riskBand, segment, search);
    }

    @GetMapping("/dashboard/value-story")
    public ValueStoryPayload valueStory(@RequestParam(name = "range", defaultValue = "30d") String range,
                                        @RequestParam(name = "region", defaultValue = "ALL") String region,
                                        @RequestParam(name = "status", defaultValue = "ALL") String status,
                                        @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                        @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                        @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.valueStory(range, region, status, riskBand, segment, search);
    }

    @GetMapping("/dashboard/top-merchants")
    public TopMerchantsPayload topMerchants(@RequestParam(name = "range", defaultValue = "30d") String range,
                                            @RequestParam(name = "region", defaultValue = "ALL") String region,
                                            @RequestParam(name = "status", defaultValue = "ALL") String status,
                                            @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                            @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                            @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.topMerchants(range, region, status, riskBand, segment, search);
    }

    @GetMapping("/dashboard/trends")
    public TrendsPayload trends(@RequestParam(name = "range", defaultValue = "30d") String range,
                                @RequestParam(name = "region", defaultValue = "ALL") String region,
                                @RequestParam(name = "status", defaultValue = "ALL") String status,
                                @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.trends(range, region, status, riskBand, segment, search);
    }

    @GetMapping("/transactions")
    public TransactionsPayload transactions(@RequestParam(name = "range", defaultValue = "30d") String range,
                                            @RequestParam(name = "region", defaultValue = "ALL") String region,
                                            @RequestParam(name = "status", defaultValue = "ALL") String status,
                                            @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                            @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                            @RequestParam(name = "search", defaultValue = "") String search,
                                            @RequestParam(name = "limit", defaultValue = "25") Integer limit,
                                            @RequestParam(name = "sortBy", defaultValue = "id") String sortBy,
                                            @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection,
                                            @RequestParam(name = "cursor", required = false) String cursor) {
        return dashboardService.transactions(range, region, status, riskBand, segment, search, limit, sortBy, sortDirection, cursor);
    }

    @GetMapping("/reviews/queue")
    public ReviewQueuePayload reviewQueue(@RequestParam(name = "range", defaultValue = "30d") String range,
                                          @RequestParam(name = "region", defaultValue = "ALL") String region,
                                          @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                          @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                          @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.reviewQueue(range, region, riskBand, segment, search);
    }

    @GetMapping("/dashboard/workbench")
    public WorkbenchPayload workbench(@RequestParam(name = "range", defaultValue = "30d") String range,
                                      @RequestParam(name = "region", defaultValue = "ALL") String region,
                                      @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                      @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                      @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.workbench(range, region, riskBand, segment, search);
    }

    @GetMapping("/dashboard/query-studio")
    public QueryStudioPayload queryStudio(@RequestParam(name = "range", defaultValue = "30d") String range,
                                          @RequestParam(name = "region", defaultValue = "ALL") String region,
                                          @RequestParam(name = "status", defaultValue = "ALL") String status,
                                          @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                          @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                          @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.queryStudio(range, region, status, riskBand, segment, search);
    }

    @GetMapping("/merchants/{merchantId}/overview")
    public MerchantOverviewPayload merchantOverview(@PathVariable("merchantId") String merchantId,
                                                    @RequestParam(name = "range", defaultValue = "30d") String range) {
        return dashboardService.merchantOverview(merchantId, range);
    }

    @GetMapping("/reports")
    public List<ReportCatalogItem> reportCatalog() {
        return dashboardService.reportCatalog();
    }

    @GetMapping("/reports/{reportId}/run")
    public ReportRunPayload runReport(@PathVariable("reportId") String reportId,
                                      @RequestParam(name = "range", defaultValue = "30d") String range,
                                      @RequestParam(name = "region", defaultValue = "ALL") String region,
                                      @RequestParam(name = "status", defaultValue = "ALL") String status,
                                      @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                      @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                      @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.runReport(reportId, range, region, status, riskBand, segment, search);
    }

    @GetMapping("/reports/{reportId}/inspect")
    public ReportInspectPayload inspectReport(@PathVariable("reportId") String reportId,
                                              @RequestParam(name = "range", defaultValue = "30d") String range,
                                              @RequestParam(name = "region", defaultValue = "ALL") String region,
                                              @RequestParam(name = "status", defaultValue = "ALL") String status,
                                              @RequestParam(name = "riskBand", defaultValue = "ALL") String riskBand,
                                              @RequestParam(name = "segment", defaultValue = "ALL") String segment,
                                              @RequestParam(name = "search", defaultValue = "") String search) {
        return dashboardService.inspectReport(reportId, range, region, status, riskBand, segment, search);
    }

    @GetMapping("/transactions/{transactionId}")
    public TransactionDetailPayload transactionDetail(@PathVariable("transactionId") String transactionId) {
        return dashboardService.transactionDetail(transactionId);
    }
}
