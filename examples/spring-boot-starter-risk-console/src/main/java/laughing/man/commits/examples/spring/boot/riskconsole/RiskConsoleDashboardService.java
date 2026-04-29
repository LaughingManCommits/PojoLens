package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.BootstrapPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.MerchantOverviewPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.QueryStudioPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportCatalogItem;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportInspectPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportRunPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReviewQueuePayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.SummaryPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TopMerchantsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionDetailPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TrendsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ValueStoryPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.WorkbenchPayload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class RiskConsoleDashboardService {

    private final RiskConsoleOverviewService overviewService;
    private final RiskConsoleTransactionsService transactionsService;
    private final RiskConsoleWorkbenchService workbenchService;
    private final RiskConsoleReportsService reportsService;

    RiskConsoleDashboardService(RiskConsoleOverviewService overviewService,
                                RiskConsoleTransactionsService transactionsService,
                                RiskConsoleWorkbenchService workbenchService,
                                RiskConsoleReportsService reportsService) {
        this.overviewService = overviewService;
        this.transactionsService = transactionsService;
        this.workbenchService = workbenchService;
        this.reportsService = reportsService;
    }

    BootstrapPayload bootstrap() {
        return overviewService.bootstrap();
    }

    SummaryPayload summary(String range,
                           String region,
                           String status,
                           String riskBand,
                           String segment,
                           String search) {
        return overviewService.summary(range, region, status, riskBand, segment, search);
    }

    ValueStoryPayload valueStory(String range,
                                 String region,
                                 String status,
                                 String riskBand,
                                 String segment,
                                 String search) {
        return overviewService.valueStory(range, region, status, riskBand, segment, search);
    }

    TopMerchantsPayload topMerchants(String range,
                                     String region,
                                     String status,
                                     String riskBand,
                                     String segment,
                                     String search) {
        return overviewService.topMerchants(range, region, status, riskBand, segment, search);
    }

    TrendsPayload trends(String range,
                         String region,
                         String status,
                         String riskBand,
                         String segment,
                         String search) {
        return overviewService.trends(range, region, status, riskBand, segment, search);
    }

    TransactionsPayload transactions(String range,
                                     String region,
                                     String status,
                                     String riskBand,
                                     String segment,
                                     String search,
                                     Integer limit,
                                     String sortBy,
                                     String sortDirection,
                                     String cursor) {
        return transactionsService.transactions(range, region, status, riskBand, segment, search, limit, sortBy, sortDirection, cursor);
    }

    ReviewQueuePayload reviewQueue(String range,
                                   String region,
                                   String riskBand,
                                   String segment,
                                   String search) {
        return workbenchService.reviewQueue(range, region, riskBand, segment, search);
    }

    WorkbenchPayload workbench(String range,
                               String region,
                               String riskBand,
                               String segment,
                               String search) {
        return workbenchService.workbench(range, region, riskBand, segment, search);
    }

    QueryStudioPayload queryStudio(String range,
                                   String region,
                                   String status,
                                   String riskBand,
                                   String segment,
                                   String search) {
        return workbenchService.queryStudio(range, region, status, riskBand, segment, search);
    }

    MerchantOverviewPayload merchantOverview(String merchantId, String range) {
        return overviewService.merchantOverview(merchantId, range);
    }

    List<ReportCatalogItem> reportCatalog() {
        return reportsService.reportCatalog();
    }

    ReportRunPayload runReport(String reportId,
                               String range,
                               String region,
                               String status,
                               String riskBand,
                               String segment,
                               String search) {
        return reportsService.runReport(reportId, range, region, status, riskBand, segment, search);
    }

    ReportInspectPayload inspectReport(String reportId,
                                       String range,
                                       String region,
                                       String status,
                                       String riskBand,
                                       String segment,
                                       String search) {
        return reportsService.inspectReport(reportId, range, region, status, riskBand, segment, search);
    }

    TransactionDetailPayload transactionDetail(String transactionId) {
        return transactionsService.transactionDetail(transactionId);
    }
}
