package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chartjs.ChartJsData;
import laughing.man.commits.chartjs.ChartJsPayload;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.AmountRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.BootstrapPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.CountRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.StatusCountRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionRecord;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.WorkbenchTablePayload;
import laughing.man.commits.natural.NaturalVocabulary;
import laughing.man.commits.sqllike.PlanPreviewField;
import laughing.man.commits.sqllike.PlanPreviewFilter;
import laughing.man.commits.sqllike.PlanPreviewJoin;
import laughing.man.commits.sqllike.PlanPreviewOrder;
import laughing.man.commits.sqllike.PlanPreviewPaging;
import laughing.man.commits.sqllike.QueryDiagnostics;
import laughing.man.commits.sqllike.QueryDiagnosticsError;
import laughing.man.commits.sqllike.QueryExecutionGuard;
import laughing.man.commits.sqllike.QueryExposurePolicy;
import laughing.man.commits.sqllike.SqlLikeLintWarning;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.sqllike.SqlLikePushdownPreview;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.stats.StatsTablePayload;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
class RiskConsoleQuerySupport {

    static final int WORKBENCH_MAX_ROWS_SCANNED = 250_000;

    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");
    private static final DecimalFormat MONEY = new DecimalFormat("$#,##0.00");
    private static final DecimalFormat RATE = new DecimalFormat("0.0%");

    private final RiskConsoleJdbcRepository repository;
    private final PojoLensRuntime pojoLensRuntime;
    private final RiskConsoleTelemetryBuffer telemetryBuffer;
    private final QueryExposurePolicy workbenchExposurePolicy;
    private final QueryExecutionGuard workbenchExecutionGuard;

    RiskConsoleQuerySupport(RiskConsoleJdbcRepository repository,
                            PojoLensRuntime pojoLensRuntime,
                            RiskConsoleTelemetryBuffer telemetryBuffer) {
        this.repository = repository;
        this.pojoLensRuntime = pojoLensRuntime;
        this.telemetryBuffer = telemetryBuffer;
        this.pojoLensRuntime.setComputedFieldRegistry(ComputedFieldRegistry.builder()
                .add("riskWeightedAmount", "amount * riskScore / 100", Double.class)
                .build());
        this.pojoLensRuntime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("merchantRegion", "merchant region", "region")
                .field("merchantName", "merchant", "merchant name")
                .field("merchantSegment", "merchant segment", "segment")
                .field("reviewStatus", "review status", "review state")
                .field("riskBand", "risk band", "risk bucket")
                .field("riskScore", "risk score")
                .field("createdAt", "created at", "created time")
                .field("amount", "amount")
                .build());
        this.pojoLensRuntime.setTelemetryListener(this.telemetryBuffer::record);
        this.workbenchExposurePolicy = QueryExposurePolicy.builder()
                .allowSources("transactions", "reviews", "merchants")
                .allowFields("id", "merchantId", "riskBand", "amount", "riskScore",
                        "reviewStatus", "priority", "analyst", "merchantRegion", "merchantSegment",
                        "riskWeightedAmount", "totalExposure", "transactionId", "reviews.transactionId",
                        "region", "segment")
                .build();
        this.workbenchExecutionGuard = QueryExecutionGuard.builder()
                .maxRowsScanned(WORKBENCH_MAX_ROWS_SCANNED)
                .maxRowsReturned(100)
                .maxComplexityScore(16)
                .maxDurationMillis(2_000)
                .build();
    }

    BootstrapPayload bootstrap() {
        return new BootstrapPayload(
                List.of("7d", "30d", "90d"),
                repository.distinctMerchantRegions(),
                repository.distinctMerchantSegments(),
                repository.distinctStatuses(),
                repository.distinctRiskBands(),
                List.of("id", "createdAt", "merchantName", "merchantRegion", "merchantSegment",
                        "status", "riskBand", "amount", "currency", "paymentMethod", "riskScore",
                        "reviewStatus", "failureCode"),
                "30d",
                25
        );
    }

    RiskConsoleJdbcRepository repository() {
        return repository;
    }

    PojoLensRuntime runtime() {
        return pojoLensRuntime;
    }

    RiskConsoleTelemetryBuffer telemetryBuffer() {
        return telemetryBuffer;
    }

    QueryExposurePolicy workbenchExposurePolicy() {
        return workbenchExposurePolicy;
    }

    QueryExecutionGuard workbenchExecutionGuard() {
        return workbenchExecutionGuard;
    }

    RiskConsoleFilterInput filters(String range,
                                   String region,
                                   String status,
                                   String riskBand,
                                   String segment,
                                   String search) {
        return new RiskConsoleFilterInput(
                normalize(range, "30d"),
                normalize(region, "ALL"),
                normalize(status, "ALL"),
                normalize(riskBand, "ALL"),
                normalize(segment, "ALL"),
                search == null ? "" : search.trim()
        );
    }

    RiskConsoleTimeWindow timeWindow(String range) {
        LocalDateTime end = LocalDateTime.now().withSecond(0).withNano(0);
        return switch (range) {
            case "7d" -> new RiskConsoleTimeWindow(end.minusDays(7), end, end.minusDays(14), "Last 7 Days");
            case "90d" -> new RiskConsoleTimeWindow(end.minusDays(90), end, end.minusDays(180), "Last 90 Days");
            case "30d" -> new RiskConsoleTimeWindow(end.minusDays(30), end, end.minusDays(60), "Last 30 Days");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad range");
        };
    }

    List<TransactionRecord> filteredRows(LocalDateTime start, LocalDateTime end, RiskConsoleFilterInput filters) {
        List<TransactionRecord> rows = repository.loadTransactionRecords(start, end);
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();
        if (!filters.region().equals("ALL")) {
            clauses.add("merchantRegion = :region");
            params.put("region", filters.region());
        }
        if (!filters.status().equals("ALL")) {
            clauses.add("status = :status");
            params.put("status", filters.status());
        }
        if (!filters.riskBand().equals("ALL")) {
            clauses.add("riskBand = :riskBand");
            params.put("riskBand", filters.riskBand());
        }
        if (!filters.segment().equals("ALL")) {
            clauses.add("merchantSegment = :segment");
            params.put("segment", filters.segment());
        }
        if (!filters.search().isBlank()) {
            clauses.add("searchText contains :search");
            params.put("search", filters.search().toLowerCase(Locale.ROOT));
        }
        String sql = clauses.isEmpty()
                ? "select *"
                : "select * where " + String.join(" and ", clauses);
        SqlLikeQuery query = pojoLensRuntime.parse(sql);
        if (!params.isEmpty()) {
            query = query.params(params);
        }
        return query.filter(rows, TransactionRecord.class);
    }

    List<TransactionRecord> statusRows(List<TransactionRecord> source, String status) {
        return pojoLensRuntime.parse("select * where status = :status")
                .params(Map.of("status", status))
                .filter(source, TransactionRecord.class);
    }

    long countAll(List<TransactionRecord> rows) {
        return rows.size();
    }

    double sumAmount(List<TransactionRecord> rows) {
        if (rows.isEmpty()) {
            return 0D;
        }
        return pojoLensRuntime.parse("select sum(amount) as totalAmount")
                .filter(rows, AmountRow.class)
                .get(0)
                .totalAmount;
    }

    double approvalRate(List<TransactionRecord> rows) {
        if (rows.isEmpty()) {
            return 0D;
        }
        List<StatusCountRow> counts = pojoLensRuntime.parse("select status, count(*) as total group by status")
                .filter(rows, StatusCountRow.class);
        long total = counts.stream().mapToLong(row -> row.total).sum();
        long approved = counts.stream()
                .filter(row -> "APPROVED".equals(row.status))
                .mapToLong(row -> row.total)
                .sum();
        return total == 0 ? 0D : (double) approved / (double) total;
    }

    long countReviewOpen(List<TransactionRecord> rows) {
        if (rows.isEmpty()) {
            return 0L;
        }
        return pojoLensRuntime.parse("select count(*) as total where reviewStatus = :open or reviewStatus = :escalated")
                .params(Map.of("open", "OPEN", "escalated", "ESCALATED"))
                .filter(rows, CountRow.class)
                .get(0)
                .total;
    }

    long countChargebackOpen(List<TransactionRecord> rows) {
        if (rows.isEmpty()) {
            return 0L;
        }
        return pojoLensRuntime.parse("select count(*) as total where chargebackOpen = :open")
                .params(Map.of("open", true))
                .filter(rows, CountRow.class)
                .get(0)
                .total;
    }

    String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    String normalizeSortBy(String sortBy) {
        return switch (normalize(sortBy, "id")) {
            case "id", "createdAt", "amount", "riskScore" -> normalize(sortBy, "id");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad sort field");
        };
    }

    String normalizeSortDirection(String sortDirection) {
        return switch (normalize(sortDirection, "desc").toLowerCase(Locale.ROOT)) {
            case "asc", "desc" -> normalize(sortDirection, "desc").toLowerCase(Locale.ROOT);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad sort direction");
        };
    }

    String orderByClause(String sortBy, String sortDirection) {
        return switch (sortBy) {
            case "id" -> "id " + sortDirection;
            case "createdAt" -> "createdAt " + sortDirection + ", id " + sortDirection;
            case "amount" -> "amount " + sortDirection + ", id " + sortDirection;
            case "riskScore" -> "riskScore " + sortDirection + ", id " + sortDirection;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad sort field");
        };
    }

    String formatWhole(long value) {
        return WHOLE.format(value);
    }

    String formatMoney(double value) {
        return MONEY.format(value);
    }

    String formatRate(double value) {
        return RATE.format(value);
    }

    String delta(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? "flat" : "new";
        }
        long percent = Math.round(((double) (current - previous) / (double) previous) * 100D);
        return percent > 0 ? "+" + percent + "%" : percent + "%";
    }

    String delta(double current, double previous) {
        if (Math.abs(previous) < 0.0001D) {
            return Math.abs(current) < 0.0001D ? "flat" : "new";
        }
        long percent = Math.round(((current - previous) / previous) * 100D);
        return percent > 0 ? "+" + percent + "%" : percent + "%";
    }

    String deltaRate(double current, double previous) {
        double points = (current - previous) * 100D;
        return String.format(Locale.ROOT, "%+.1f pt", points);
    }

    WorkbenchTablePayload tablePayload(StatsTablePayload payload, String source) {
        return new WorkbenchTablePayload(payload.columns(), payload.rows(), payload.totals(), source);
    }

    Map<String, Object> exposurePolicyMap(QueryExposurePolicy policy) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("allowedFields", policy.allowedFields());
        map.put("allowedSources", policy.allowedSources());
        map.put("restrictsFields", policy.restrictsFields());
        map.put("restrictsSources", policy.restrictsSources());
        return map;
    }

    Map<String, Object> executionGuardMap(QueryExecutionGuard guard) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("maxRowsScanned", guard.maxRowsScanned());
        map.put("maxRowsReturned", guard.maxRowsReturned());
        map.put("maxComplexityScore", guard.maxComplexityScore());
        map.put("maxDurationMillis", guard.maxDurationMillis());
        map.put("hasPreExecutionLimits", guard.hasPreExecutionLimits());
        return map;
    }

    Map<String, Object> planPreviewMap(SqlLikePlanPreview preview) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", preview.source());
        map.put("wildcard", preview.isWildcard());
        map.put("requiredParams", preview.requiredParams());
        map.put("groupByFields", preview.groupByFields());
        map.put("hasGrouping", preview.hasGrouping());
        map.put("hasAggregation", preview.hasAggregation());
        map.put("hasWindows", preview.hasWindows());
        map.put("hasJoins", preview.hasJoins());
        map.put("hasPaging", preview.hasPaging());
        map.put("hasSubqueries", preview.hasSubqueries());
        map.put("selectFields", preview.selectFields().stream().map(this::planPreviewFieldMap).toList());
        map.put("filters", preview.filters().stream().map(this::planPreviewFilterMap).toList());
        map.put("havingFilters", preview.havingFilters().stream().map(this::planPreviewFilterMap).toList());
        map.put("qualifyFilters", preview.qualifyFilters().stream().map(this::planPreviewFilterMap).toList());
        map.put("orderFields", preview.orderFields().stream().map(this::planPreviewOrderMap).toList());
        map.put("joins", preview.joins().stream().map(this::planPreviewJoinMap).toList());
        map.put("paging", preview.paging() == null ? null : planPreviewPagingMap(preview.paging()));
        return map;
    }

    Map<String, Object> diagnosticsMap(QueryDiagnostics diagnostics) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("valid", diagnostics.valid());
        map.put("requiredParams", diagnostics.requiredParams());
        map.put("referencedFields", diagnostics.referencedFields());
        map.put("outputFields", diagnostics.outputFields());
        map.put("joinSources", diagnostics.joinSources());
        map.put("hasSubqueries", diagnostics.hasSubqueries());
        map.put("errors", diagnostics.errors().stream().map(this::diagnosticsErrorMap).toList());
        map.put("lintWarnings", diagnostics.lintWarnings().stream().map(this::lintWarningMap).toList());
        return map;
    }

    Map<String, Object> pushdownPreviewMap(SqlLikePushdownPreview preview) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", preview.source());
        map.put("mode", preview.mode().name());
        map.put("pushableStages", preview.pushableStages());
        map.put("inMemoryStages", preview.inMemoryStages());
        map.put("fallbackReasons", preview.fallbackReasons());
        map.put("fullyPushable", preview.isFullyPushable());
        map.put("splitExecution", preview.requiresSplitExecution());
        map.put("inMemoryOnly", preview.isInMemoryOnly());
        return map;
    }

    ChartJsPayload emptyChartPayload(ChartType type, String title, String xLabel, String yLabel) {
        String chartType = type == null
                ? "bar"
                : (type == ChartType.AREA ? "line" : type.name().toLowerCase(Locale.ROOT));
        return new ChartJsPayload(
                chartType,
                new ChartJsData(List.of(), List.of()),
                Map.of(
                        "responsive", true,
                        "maintainAspectRatio", false,
                        "plugins", Map.of(
                                "legend", Map.of("display", true, "position", "bottom"),
                                "title", Map.of("display", true, "text", title)
                        ),
                        "pojoLens", Map.of(
                                "stacked", false,
                                "percentStacked", false,
                                "nullPointPolicy", "PRESERVE",
                                "xLabel", xLabel,
                                "yLabel", yLabel
                        )
                )
        );
    }

    private Map<String, Object> planPreviewFieldMap(PlanPreviewField field) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("field", field.field());
        map.put("outputName", field.outputName());
        map.put("alias", field.alias());
        map.put("metric", field.metric());
        map.put("timeBucket", field.timeBucket());
        map.put("windowFunction", field.windowFunction());
        map.put("windowPartitionFields", field.windowPartitionFields());
        map.put("windowOrderFields", field.windowOrderFields());
        map.put("windowFrame", field.windowFrame());
        map.put("computed", field.isComputed());
        map.put("countAll", field.isCountAll());
        return map;
    }

    private Map<String, Object> planPreviewFilterMap(PlanPreviewFilter filter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("field", filter.field());
        map.put("operator", filter.operator());
        map.put("valueKind", filter.valueKind());
        map.put("parameterName", filter.parameterName());
        if (filter.subqueryPreview() != null) {
            map.put("subquery", planPreviewMap(filter.subqueryPreview()));
        }
        return map;
    }

    private Map<String, Object> planPreviewOrderMap(PlanPreviewOrder order) {
        return Map.of("field", order.field(), "direction", order.direction());
    }

    private Map<String, Object> planPreviewJoinMap(PlanPreviewJoin join) {
        return Map.of(
                "type", join.type(),
                "source", join.source(),
                "parentField", join.parentField(),
                "childField", join.childField()
        );
    }

    private Map<String, Object> planPreviewPagingMap(PlanPreviewPaging paging) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("limit", paging.limit());
        map.put("limitParameter", paging.limitParameter());
        map.put("offset", paging.offset());
        map.put("offsetParameter", paging.offsetParameter());
        map.put("hasLimit", paging.hasLimit());
        map.put("hasOffset", paging.hasOffset());
        return map;
    }

    private Map<String, Object> diagnosticsErrorMap(QueryDiagnosticsError error) {
        return Map.of("code", error.code(), "message", error.message());
    }

    private Map<String, Object> lintWarningMap(SqlLikeLintWarning warning) {
        return Map.of("code", warning.code(), "message", warning.message());
    }
}
