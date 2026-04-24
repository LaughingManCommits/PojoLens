package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chartjs.ChartJsPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.MerchantTotalReportRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.RegionCountReportRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportCatalogItem;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportInspectPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReportRunPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.RiskBandCountReportRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionRecord;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.report.SavedReport;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.table.TabularRows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class RiskConsoleReportsService {

    private final RiskConsoleQuerySupport support;
    private final Map<String, ReportSpec<?>> reports;

    RiskConsoleReportsService(RiskConsoleQuerySupport support) {
        this.support = support;
        this.reports = createReports();
    }

    List<ReportCatalogItem> reportCatalog() {
        return reports.values().stream()
                .map(spec -> new ReportCatalogItem(
                        spec.report().id(),
                        spec.report().name(),
                        spec.report().kind().name(),
                        spec.summary(),
                        spec.category(),
                        spec.report().defaultParams(),
                        spec.report().chartSpec() != null
                ))
                .toList();
    }

    int reportCount() {
        return reports.size();
    }

    ReportRunPayload runReport(String reportId,
                               String range,
                               String region,
                               String status,
                               String riskBand,
                               String segment,
                               String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, status, riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> rows = support.filteredRows(window.start(), window.end(), filters);
        return reportSpec(reportId).run(rows, support);
    }

    ReportInspectPayload inspectReport(String reportId,
                                       String range,
                                       String region,
                                       String status,
                                       String riskBand,
                                       String segment,
                                       String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, status, riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> rows = support.filteredRows(window.start(), window.end(), filters);
        return reportSpec(reportId).inspect(rows, support);
    }

    private Map<String, ReportSpec<?>> createReports() {
        Map<String, ReportSpec<?>> specs = new LinkedHashMap<>();

        SavedReport highExposure = SavedReport.sqlLike(
                        "high-exposure-merchants",
                        "High Exposure Merchants",
                        "select merchantName, sum(amount) as totalAmount "
                                + "group by merchantName having totalAmount >= :minAmount "
                                + "order by totalAmount desc limit 10")
                .withDefaultParam("minAmount", 250_000D)
                .withChartSpec(ChartSpec.of(ChartType.BAR, "merchantName", "totalAmount")
                        .withTitle("High Exposure Merchants")
                        .withAxisLabels("Merchant", "Amount"));
        specs.put(highExposure.id(), new ReportSpec<>(
                highExposure,
                MerchantTotalReportRow.class,
                "Top merchants by processed amount. Uses grouping and HAVING.",
                "Exposure"
        ));

        SavedReport riskBandMix = SavedReport.sqlLike(
                        "risk-band-mix",
                        "Risk Band Mix Report",
                        "select riskBand, count(*) as total group by riskBand order by riskBand asc")
                .withChartSpec(ChartSpec.of(ChartType.BAR, "riskBand", "total")
                        .withTitle("Risk Band Mix")
                        .withAxisLabels("Risk Band", "Transactions"));
        specs.put(riskBandMix.id(), new ReportSpec<>(
                riskBandMix,
                RiskBandCountReportRow.class,
                "Counts by risk band. Good quick shape check for dashboard filters.",
                "Risk"
        ));

        SavedReport regionalDeclines = SavedReport.sqlLike(
                        "regional-declines",
                        "Regional Declines",
                        "select merchantRegion, count(*) as total where status = :status "
                                + "group by merchantRegion order by total desc limit 8")
                .withDefaultParam("status", "DECLINED")
                .withChartSpec(ChartSpec.of(ChartType.BAR, "merchantRegion", "total")
                        .withTitle("Regional Declines")
                        .withAxisLabels("Region", "Declines"));
        specs.put(regionalDeclines.id(), new ReportSpec<>(
                regionalDeclines,
                RegionCountReportRow.class,
                "Decline count by region. Uses default params inside saved report contract.",
                "Operations"
        ));

        SavedReport naturalOpenReviews = SavedReport.natural(
                        "open-reviews-by-region-natural",
                        "Open Reviews by Region (Natural)",
                        "from transactions show merchantRegion, count of transactions as total "
                                + "where reviewStatus is :open or reviewStatus is :escalated "
                                + "group by merchantRegion sort by total descending")
                .withDefaultParams(Map.of("open", "OPEN", "escalated", "ESCALATED"))
                .withChartSpec(ChartSpec.of(ChartType.BAR, "merchantRegion", "total")
                        .withTitle("Open Reviews by Region")
                        .withAxisLabels("Region", "Open Reviews"));
        specs.put(naturalOpenReviews.id(), new ReportSpec<>(
                naturalOpenReviews,
                RegionCountReportRow.class,
                "Guided plain-English report contract with reusable chart output.",
                "Natural"
        ));

        return Map.copyOf(specs);
    }

    private ReportSpec<?> reportSpec(String reportId) {
        ReportSpec<?> spec = reports.get(support.normalize(reportId, ""));
        if (spec == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        return spec;
    }

    private record ReportSpec<T>(SavedReport report,
                                 Class<T> rowType,
                                 String summary,
                                 String category) {

        private ReportDefinition<T> definition() {
            return report.toDefinition(rowType);
        }

        private ReportRunPayload run(List<TransactionRecord> rows, RiskConsoleQuerySupport support) {
            ReportDefinition<T> definition = definition();
            if (rows.isEmpty()) {
                ChartJsPayload chart = report.chartSpec() == null
                        ? null
                        : support.emptyChartPayload(report.chartSpec().type(), report.chartSpec().title(),
                        report.chartSpec().xLabel(), report.chartSpec().yLabel());
                return new ReportRunPayload(
                        report.id(),
                        report.name(),
                        definition.schema().names(),
                        List.of(),
                        chart,
                        report.source()
                );
            }
            List<T> reportRows = definition.rows(rows);
            ChartJsPayload chart = report.chartSpec() == null ? null : definition.chartJs(rows);
            return new ReportRunPayload(
                    report.id(),
                    report.name(),
                    definition.schema().names(),
                    TabularRows.toMaps(reportRows, definition.schema()),
                    chart,
                    report.source()
            );
        }

        private ReportInspectPayload inspect(List<TransactionRecord> rows, RiskConsoleQuerySupport support) {
            ReportDefinition<T> definition = definition();
            Map<String, Object> explain;
            Map<String, Object> pushdownPreview;
            if ("NATURAL".equals(report.kind().name())) {
                var naturalQuery = report.toNaturalQuery();
                SqlLikeQuery pushdownQuery = SqlLikeQuery.of(naturalQuery.equivalentSqlLike());
                pushdownPreview = support.pushdownPreviewMap(pushdownQuery.pushdownPreview());
                explain = rows.isEmpty() ? naturalQuery.explain() : naturalQuery.explain(rows, rowType);
            } else {
                SqlLikeQuery query = report.toQuery();
                pushdownPreview = support.pushdownPreviewMap(query.pushdownPreview());
                explain = rows.isEmpty() ? query.explain() : query.explain(rows, rowType);
            }
            return new ReportInspectPayload(
                    report.id(),
                    report.name(),
                    report.queryText(),
                    report.defaultParams(),
                    definition.schema().names(),
                    support.planPreviewMap(report.planPreview()),
                    support.diagnosticsMap(report.diagnostics()),
                    pushdownPreview,
                    explain
            );
        }
    }
}
