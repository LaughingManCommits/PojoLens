package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.AnalystWorkloadRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.CancellationDemoPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.CancellationProbeRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.Merchant;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.NaturalQueryStudioPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.PaymentTransaction;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.QueryStudioPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.RegionCountReportRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReviewQueuePayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ReviewQueueRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.RiskReview;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionRecord;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TypedQueryStudioPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TypedWatchlistRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.WorkbenchPayload;
import laughing.man.commits.report.ReportDefinition;
import laughing.man.commits.sqllike.QueryCancellationToken;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExecutionGuardException;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.stats.StatsTablePayload;
import laughing.man.commits.stats.StatsViewPresets;
import laughing.man.commits.table.TabularRows;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
class RiskConsoleWorkbenchService {

    private final RiskConsoleQuerySupport support;

    RiskConsoleWorkbenchService(RiskConsoleQuerySupport support) {
        this.support = support;
    }

    ReviewQueuePayload reviewQueue(String range,
                                   String region,
                                   String riskBand,
                                   String segment,
                                   String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, "ALL", riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> rows = support.filteredRows(window.start(), window.end(), filters);
        SqlLikeQuery query = support.runtime().parse(
                        "select reviewStatus, id, createdAt, merchantName, merchantRegion, amount, riskScore, riskBand, "
                                + "row_number() over (partition by reviewStatus order by riskScore desc, createdAt desc, id desc) as queueRank "
                                + "where reviewStatus = :open or reviewStatus = :escalated "
                                + "qualify queueRank <= 8 order by reviewStatus asc, queueRank asc")
                .params(Map.of("open", "OPEN", "escalated", "ESCALATED"));
        List<ReviewQueueRow> queue = query.filter(rows, ReviewQueueRow.class);
        return new ReviewQueuePayload(
                query.schema(ReviewQueueRow.class).names(),
                TabularRows.toMaps(queue, query.schema(ReviewQueueRow.class)),
                query.source()
        );
    }

    WorkbenchPayload workbench(String range,
                               String region,
                               String riskBand,
                               String segment,
                               String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, "ALL", riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> snapshotRows = support.filteredRows(window.start(), window.end(), filters);
        var riskTierPreset = StatsViewPresets.by("merchantRiskTier", Metric.SUM, "amount", "totalAmount");
        SqlLikeQuery analystQuery = workbenchAnalystQuery(filters);

        if (snapshotRows.isEmpty()) {
            return new WorkbenchPayload(
                    support.tablePayload(StatsTablePayload.of(riskTierPreset.schema(), List.of(), Map.of()), "stats-preset"),
                    new RiskConsoleTypes.WorkbenchTablePayload(
                            analystQuery.schema(AnalystWorkloadRow.class).names(),
                            List.of(),
                            Map.of("reviewCount", 0),
                            analystQuery.source()
                    ),
                    new ArrayList<>(support.runtime().getComputedFieldRegistry().names()),
                    support.exposurePolicyMap(support.workbenchExposurePolicy()),
                    support.executionGuardMap(support.workbenchExecutionGuard()),
                    support.telemetryBuffer().snapshot(8),
                    analystQuery.explain()
            );
        }

        StatsTablePayload riskTierStats = riskTierPreset.tablePayload(snapshotRows);
        List<PaymentTransaction> transactions = support.repository().loadPaymentTransactions(window.start(), window.end());
        List<RiskReview> reviews = support.repository().loadRiskReviews();
        List<Merchant> merchants = support.repository().loadMerchants();
        DatasetBundle bundle = DatasetBundle.builder(transactions)
                .add("reviews", reviews)
                .add("merchants", merchants)
                .build();

        List<AnalystWorkloadRow> analystRows = analystQuery.filter(bundle, AnalystWorkloadRow.class);

        return new WorkbenchPayload(
                support.tablePayload(riskTierStats, "stats-preset"),
                new RiskConsoleTypes.WorkbenchTablePayload(
                        analystQuery.schema(AnalystWorkloadRow.class).names(),
                        TabularRows.toMaps(analystRows, analystQuery.schema(AnalystWorkloadRow.class)),
                        Map.of("reviewCount", analystRows.stream().mapToLong(row -> row.reviewCount).sum()),
                        analystQuery.source()
                ),
                new ArrayList<>(support.runtime().getComputedFieldRegistry().names()),
                support.exposurePolicyMap(support.workbenchExposurePolicy()),
                support.executionGuardMap(support.workbenchExecutionGuard()),
                support.telemetryBuffer().snapshot(8),
                analystQuery.explain(bundle, AnalystWorkloadRow.class)
        );
    }

    QueryStudioPayload queryStudio(String range,
                                   String region,
                                   String status,
                                   String riskBand,
                                   String segment,
                                   String search) {
        RiskConsoleFilterInput filters = support.filters(range, region, status, riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        List<TransactionRecord> rows = support.filteredRows(window.start(), window.end(), filters);
        return new QueryStudioPayload(
                naturalQueryStudio(rows),
                typedQueryStudio(rows),
                cancellationDemo(rows)
        );
    }

    private SqlLikeQuery workbenchAnalystQuery(RiskConsoleFilterInput filters) {
        StringBuilder sql = new StringBuilder(
                "select analyst, priority, reviewStatus, region as merchantRegion, count(*) as reviewCount, "
                        + "sum(riskWeightedAmount) as totalExposure "
                        + "from transactions "
                        + "left join reviews on transactions.id = reviews.transactionId "
                        + "left join merchants on transactions.merchantId = merchants.id "
                        + "where (reviewStatus = :open or reviewStatus = :escalated)");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("open", "OPEN");
        params.put("escalated", "ESCALATED");
        if (!filters.riskBand().equals("ALL")) {
            sql.append(" and riskBand = :riskBand");
            params.put("riskBand", filters.riskBand());
        }
        if (!filters.region().equals("ALL")) {
            sql.append(" and region = :region");
            params.put("region", filters.region());
        }
        if (!filters.segment().equals("ALL")) {
            sql.append(" and segment = :segment");
            params.put("segment", filters.segment());
        }
        if (!filters.search().isBlank()) {
            sql.append(" and analyst contains :search");
            params.put("search", filters.search().toLowerCase(Locale.ROOT));
        }
        sql.append(" group by analyst, priority, reviewStatus, region")
                .append(" order by totalExposure desc, analyst desc limit 8");
        return support.runtime().parse(sql.toString())
                .params(params)
                .exposurePolicy(support.workbenchExposurePolicy())
                .executionGuard(support.workbenchExecutionGuard());
    }

    private NaturalQueryStudioPayload naturalQueryStudio(List<TransactionRecord> rows) {
        String queryText = "from transactions show merchant region as merchantRegion, count of transactions as total "
                + "where review state is :open or review state is :escalated "
                + "group by merchant region sort by total descending as bar chart";
        var naturalQuery = support.runtime().natural()
                .parse(queryText)
                .params(Map.of("open", "OPEN", "escalated", "ESCALATED"));
        ReportDefinition<RegionCountReportRow> definition = ReportDefinition.natural(
                naturalQuery,
                RegionCountReportRow.class,
                ChartSpec.of(ChartType.BAR, "merchantRegion", "total")
                        .withTitle("Open Reviews by Region")
                        .withAxisLabels("Region", "Open Reviews")
        );
        List<RegionCountReportRow> resultRows = rows.isEmpty() ? List.of() : definition.rows(rows);
        return new NaturalQueryStudioPayload(
                queryText,
                definition.schema().names(),
                TabularRows.toMaps(resultRows, definition.schema()),
                rows.isEmpty()
                        ? support.emptyChartPayload(ChartType.BAR, "Open Reviews by Region", "Region", "Open Reviews")
                        : definition.chartJs(rows),
                definition.schema().names(),
                rows.isEmpty() ? naturalQuery.explain() : naturalQuery.explain(rows, RegionCountReportRow.class)
        );
    }

    private TypedQueryStudioPayload typedQueryStudio(List<TransactionRecord> rows) {
        TypedQuery<TransactionRecord> query = TypedQuery.from(TransactionRecord.class)
                .select(
                        TransactionRecordTypedFields.ID,
                        TransactionRecordTypedFields.MERCHANT_NAME,
                        TransactionRecordTypedFields.MERCHANT_REGION,
                        TransactionRecordTypedFields.REVIEW_STATUS,
                        TransactionRecordTypedFields.RISK_BAND,
                        TransactionRecordTypedFields.RISK_SCORE,
                        TransactionRecordTypedFields.AMOUNT)
                .where(TransactionRecordTypedFields.REVIEW_STATUS.in(List.of("OPEN", "ESCALATED")))
                .orderByDesc(TransactionRecordTypedFields.RISK_SCORE)
                .limit(8);
        if (rows.isEmpty()) {
            return new TypedQueryStudioPayload(
                    List.of("id", "merchantName", "merchantRegion", "reviewStatus", "riskBand", "riskScore", "amount"),
                    List.of(),
                    List.of("id", "merchantName", "merchantRegion", "reviewStatus", "riskBand", "riskScore", "amount"),
                    Map.of()
            );
        }
        var schema = query.schema(rows, TypedWatchlistRow.class);
        List<TypedWatchlistRow> typedRows = query.filter(rows, TypedWatchlistRow.class);
        return new TypedQueryStudioPayload(
                schema.names(),
                TabularRows.toMaps(typedRows, schema),
                schema.names(),
                query.explain(rows)
        );
    }

    private CancellationDemoPayload cancellationDemo(List<TransactionRecord> rows) {
        String queryText = "select id, merchantName, reviewStatus, riskScore "
                + "where reviewStatus = :open or reviewStatus = :escalated "
                + "order by riskScore desc, id desc limit 12";
        AtomicBoolean cancel = new AtomicBoolean(false);
        QueryExecutionGuard guard = QueryExecutionGuard.builder()
                .cancellationToken(QueryCancellationToken.ofAtomic(cancel))
                .build();
        SqlLikeQuery query = support.runtime().parse(queryText)
                .params(Map.of("open", "OPEN", "escalated", "ESCALATED"))
                .executionGuard(guard);
        var schema = query.schema(CancellationProbeRow.class);
        List<CancellationProbeRow> emittedRows = new ArrayList<>();
        try {
            query.stream(rows, CancellationProbeRow.class).forEach(row -> {
                emittedRows.add(row);
                if (emittedRows.size() == 1) {
                    cancel.set(true);
                }
            });
            return new CancellationDemoPayload(
                    queryText,
                    false,
                    null,
                    emittedRows.size(),
                    schema.names(),
                    TabularRows.toMaps(emittedRows, schema),
                    support.executionGuardMap(guard)
            );
        } catch (QueryExecutionGuardException ex) {
            return new CancellationDemoPayload(
                    queryText,
                    true,
                    ex.outcome().blockCode(),
                    ex.outcome().rowsReturnedBeforeAbort(),
                    schema.names(),
                    TabularRows.toMaps(emittedRows, schema),
                    support.executionGuardMap(guard)
            );
        }
    }
}
