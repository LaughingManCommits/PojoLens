package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chart.NullPointPolicy;
import laughing.man.commits.chart.ChartQueryPresets;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.BootstrapPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.Merchant;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.MerchantLeaderboardRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.MerchantOverviewHeader;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.MerchantOverviewPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.SummaryCard;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.SummaryPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TopMerchantsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionRecord;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionTableRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TrendsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ValueMetric;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ValueStoryPayload;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.table.TabularRows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
class RiskConsoleOverviewService {

    private final RiskConsoleQuerySupport support;
    private final RiskConsoleReportsService reportsService;

    RiskConsoleOverviewService(RiskConsoleQuerySupport support,
                               RiskConsoleReportsService reportsService) {
        this.support = support;
        this.reportsService = reportsService;
    }

    BootstrapPayload bootstrap() {
        return support.bootstrap();
    }

    SummaryPayload summary(String range,
                           String region,
                           String status,
                           String riskBand,
                           String segment,
                           String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, status, riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> current = support.filteredRows(window.start(), window.end(), filters);
        List<TransactionRecord> previous = support.filteredRows(window.previousStart(), window.start(), filters);

        long currentVolume = support.countAll(current);
        double currentAmount = support.sumAmount(current);
        double currentApprovalRate = support.approvalRate(current);
        long currentReviewOpen = support.countReviewOpen(current);
        long currentChargebacks = support.countChargebackOpen(current);

        long previousVolume = support.countAll(previous);
        double previousAmount = support.sumAmount(previous);
        double previousApprovalRate = support.approvalRate(previous);
        long previousReviewOpen = support.countReviewOpen(previous);
        long previousChargebacks = support.countChargebackOpen(previous);

        List<SummaryCard> cards = List.of(
                new SummaryCard("volume", "Transactions", support.formatWhole(currentVolume),
                        support.delta(currentVolume, previousVolume), "neutral"),
                new SummaryCard("amount", "Processed Amount", support.formatMoney(currentAmount),
                        support.delta(currentAmount, previousAmount), "neutral"),
                new SummaryCard("approvalRate", "Approval Rate", support.formatRate(currentApprovalRate),
                        support.deltaRate(currentApprovalRate, previousApprovalRate),
                        currentApprovalRate >= previousApprovalRate ? "good" : "warn"),
                new SummaryCard("reviewOpen", "Open Reviews", support.formatWhole(currentReviewOpen),
                        support.delta(currentReviewOpen, previousReviewOpen),
                        currentReviewOpen <= previousReviewOpen ? "good" : "warn"),
                new SummaryCard("chargebacks", "Open Chargebacks", support.formatWhole(currentChargebacks),
                        support.delta(currentChargebacks, previousChargebacks),
                        currentChargebacks <= previousChargebacks ? "good" : "bad")
        );

        return new SummaryPayload(window.label(), current.size(), cards);
    }

    ValueStoryPayload valueStory(String range,
                                 String region,
                                 String status,
                                 String riskBand,
                                 String segment,
                                 String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, status, riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> rows = support.filteredRows(window.start(), window.end(), filters);

        return new ValueStoryPayload(
                "Why PojoLens matters here",
                "JDBC loads a bounded time window. PojoLens applies the active filters in memory, then reshapes that filtered snapshot into cards, charts, ranked queues, paged tables, and saved report contracts.",
                List.of(
                        new ValueMetric("Filtered snapshot",
                                support.formatWhole(rows.size()) + " rows",
                                "Current scope after JDBC load and PojoLens filters"),
                        new ValueMetric("Dashboard outputs",
                                "8 surfaces",
                                "Cards, trends, merchants, queue, transactions, reports, workbench, query studio"),
                        new ValueMetric("Saved contracts",
                                support.formatWhole(reportsService.reportCount()) + " reports",
                                "Reusable report definitions with preview, diagnostics, pushdown, and explain"),
                        new ValueMetric("Query shapes",
                                "11 features",
                                "Filter, sort/page, group, aggregate, charts, windows, joins, natural, typed, cancellation, governance")
                ),
                List.of(
                        "Summary cards reuse one filtered snapshot for totals, approval rate, open reviews, and chargebacks.",
                        "Trend panels come from PojoLens chart presets instead of custom chart DTO code per panel.",
                        "Review queue uses a window query with QUALIFY, not hand-built Java ranking logic.",
                        "Workbench proves stats presets, dataset-bundle joins, computed fields, policy, guard, telemetry, and explain all work on the same product surface.",
                        "Query Studio runs guided natural text, typed DSL, and cancellation-aware streaming over the same filtered snapshot."
                ),
                List.of(
                        "Filter in memory",
                        "Sort + keyset page",
                        "Group + aggregate",
                        "Chart presets",
                        "Window ranking",
                        "Natural query",
                        "Typed DSL",
                        "Saved reports",
                        "Cancellation",
                        "Explain + diagnostics",
                        "Policy + guard"
                )
        );
    }

    TopMerchantsPayload topMerchants(String range,
                                     String region,
                                     String status,
                                     String riskBand,
                                     String segment,
                                     String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, status, riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> rows = support.filteredRows(window.start(), window.end(), filters);
        SqlLikeQuery query = support.runtime().parse(
                "select merchantId, merchantName, merchantRegion, merchantSegment, sum(amount) as totalAmount, count(*) as transactionCount "
                        + "group by merchantId, merchantName, merchantRegion, merchantSegment "
                        + "order by totalAmount desc, merchantName desc limit 8");
        if (rows.isEmpty()) {
            return new TopMerchantsPayload(
                    query.schema(MerchantLeaderboardRow.class).names(),
                    List.of(),
                    Map.of("merchantCount", 0),
                    query.source()
            );
        }
        List<MerchantLeaderboardRow> leaderboard = query.filter(rows, MerchantLeaderboardRow.class);
        return new TopMerchantsPayload(
                query.schema(MerchantLeaderboardRow.class).names(),
                TabularRows.toMaps(leaderboard, query.schema(MerchantLeaderboardRow.class)),
                Map.of("merchantCount", leaderboard.size()),
                query.source()
        );
    }

    TrendsPayload trends(String range,
                         String region,
                         String status,
                         String riskBand,
                         String segment,
                         String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, status, riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> rows = support.filteredRows(window.start(), window.end(), filters);
        if (rows.isEmpty()) {
            return new TrendsPayload(
                    support.emptyChartPayload(ChartType.LINE, "Transaction Volume", "Day", "Transactions"),
                    support.emptyChartPayload(ChartType.AREA, "Declines", "Day", "Declines"),
                    support.emptyChartPayload(ChartType.BAR, "Risk Band Mix", "Risk Band", "Transactions"),
                    support.emptyChartPayload(ChartType.BAR, "Regional Status Mix", "Region", "Transactions"),
                    support.emptyChartPayload(ChartType.PIE, "Payment Method Share", "Method", "Transactions"),
                    support.emptyChartPayload(ChartType.BAR, "Decline Reasons", "Reason", "Declines")
            );
        }
        List<TransactionRecord> declined = support.statusRows(rows, "DECLINED");

        return new TrendsPayload(
                ChartQueryPresets.timeSeriesCounts("createdAt", TimeBucket.DAY, "period", "value")
                        .mapChartSpec(spec -> spec.withType(ChartType.LINE)
                                .withTitle("Transaction Volume")
                                .withAxisLabels("Day", "Transactions"))
                        .chartJs(rows),
                declined.isEmpty()
                        ? support.emptyChartPayload(ChartType.AREA, "Declines", "Day", "Declines")
                        : ChartQueryPresets.timeSeriesCounts("createdAt", TimeBucket.DAY, "period", "value")
                                .mapChartSpec(spec -> spec.withType(ChartType.AREA)
                                        .withTitle("Declines")
                                        .withAxisLabels("Day", "Declines"))
                                .chartJs(declined),
                ChartQueryPresets.categoryCounts("riskBand", "value")
                        .mapChartSpec(spec -> spec.withType(ChartType.BAR)
                                .withTitle("Risk Band Mix")
                                .withAxisLabels("Risk Band", "Transactions")
                                .withSortedLabels(true))
                        .chartJs(rows),
                ChartQueryPresets.groupedBreakdown("merchantRegion", "status", Metric.COUNT, null, "value")
                        .mapChartSpec(spec -> spec.withType(ChartType.BAR)
                                .withTitle("Regional Status Mix")
                                .withAxisLabels("Region", "Transactions")
                                .withSortedLabels(true)
                                .withStacked(true)
                                .withNullPointPolicy(NullPointPolicy.ZERO))
                        .chartJs(rows),
                ChartQueryPresets.categoryTotals("paymentMethod", Metric.COUNT, null, "value")
                        .mapChartSpec(spec -> spec.withType(ChartType.PIE)
                                .withTitle("Payment Method Share")
                                .withSortedLabels(true))
                        .chartJs(rows),
                declined.isEmpty()
                        ? support.emptyChartPayload(ChartType.BAR, "Decline Reasons", "Reason", "Declines")
                        : ChartQueryPresets.categoryCounts("failureCode", "value")
                                .mapChartSpec(spec -> spec.withType(ChartType.BAR)
                                        .withTitle("Decline Reasons")
                                        .withAxisLabels("Reason", "Declines")
                                        .withSortedLabels(true))
                                .chartJs(declined)
        );
    }

    MerchantOverviewPayload merchantOverview(String merchantId, String range) {
        Merchant merchant = support.repository().loadMerchant(merchantId);
        if (merchant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found");
        }
        RiskConsoleTimeWindow window = support.timeWindow(support.normalize(range, "30d"));
        List<TransactionRecord> rows = support.repository().loadMerchantTransactionRecords(merchantId, window.start(), window.end());
        MerchantOverviewHeader header = new MerchantOverviewHeader(
                merchant.id,
                merchant.name,
                merchant.segment,
                merchant.region,
                merchant.riskTier,
                support.countAll(rows),
                support.sumAmount(rows),
                support.approvalRate(rows)
        );
        SqlLikeQuery recentQuery = support.runtime().parse(
                "select id, createdAt, merchantName, merchantRegion, merchantSegment, status, riskBand, amount, "
                        + "currency, paymentMethod, riskScore, reviewStatus, failureCode "
                        + "order by createdAt desc, id desc limit 8");
        List<TransactionTableRow> recentRows = rows.isEmpty() ? List.of() : recentQuery.filter(rows, TransactionTableRow.class);
        return new MerchantOverviewPayload(
                header,
                rows.isEmpty()
                        ? support.emptyChartPayload(ChartType.LINE, "Merchant Volume", "Day", "Transactions")
                        : ChartQueryPresets.timeSeriesCounts("createdAt", TimeBucket.DAY, "period", "value")
                                .mapChartSpec(spec -> spec.withType(ChartType.LINE)
                                        .withTitle("Merchant Volume")
                                        .withAxisLabels("Day", "Transactions"))
                                .chartJs(rows),
                recentQuery.schema(TransactionTableRow.class).names(),
                TabularRows.toMaps(recentRows, recentQuery.schema(TransactionTableRow.class))
        );
    }
}
