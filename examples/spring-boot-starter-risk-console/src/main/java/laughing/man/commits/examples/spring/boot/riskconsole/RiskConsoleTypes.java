package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.chartjs.ChartJsPayload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class RiskConsoleTypes {

    private RiskConsoleTypes() {
    }

    public static final class Merchant {
        public String id;
        public String name;
        public String segment;
        public String region;
        public String riskTier;
        public String ownerTeam;
        public LocalDateTime onboardedAt;
        public boolean active;
    }

    public static final class CustomerAccount {
        public String id;
        public String emailHash;
        public String country;
        public LocalDateTime signupAt;
        public String kycStatus;
        public double lifetimeValue;
        public int chargebackCount;
    }

    public static final class PaymentTransaction {
        public String id;
        public String merchantId;
        public String customerId;
        public LocalDateTime createdAt;
        public double amount;
        public String currency;
        public String paymentMethod;
        public String cardBrand;
        public String status;
        public int riskScore;
        public String riskBand;
        public String failureCode;
    }

    public static final class RiskReview {
        public String id;
        public String transactionId;
        public String queueName;
        public String priority;
        public String reasonCode;
        public String analyst;
        public LocalDateTime openedAt;
        public LocalDateTime resolvedAt;
        public String reviewStatus;
        public String resolution;
    }

    public static final class ChargebackCase {
        public String id;
        public String transactionId;
        public String merchantId;
        public String reasonCode;
        public String stage;
        public double amount;
        public LocalDateTime openedAt;
        public LocalDateTime dueAt;
        public String outcome;
    }

    public static final class TransactionEvent {
        public String id;
        public String transactionId;
        public String eventType;
        public LocalDateTime createdAt;
        public String actor;
        public String detailText;
    }

    public static final class TransactionRecord {
        public String id;
        public String merchantId;
        public String merchantName;
        public String merchantSegment;
        public String merchantRegion;
        public String merchantRiskTier;
        public String customerId;
        public String customerCountry;
        public LocalDateTime createdAt;
        public double amount;
        public String currency;
        public String paymentMethod;
        public String cardBrand;
        public String status;
        public int riskScore;
        public String riskBand;
        public String failureCode;
        public String reviewStatus;
        public boolean chargebackOpen;
        public String chargebackStage;
        public String searchText;
    }

    public static final class CountRow {
        public long total;
    }

    public static final class AmountRow {
        public double totalAmount;
    }

    public static final class StatusCountRow {
        public String status;
        public long total;
    }

    public static final class MerchantLeaderboardRow {
        public String merchantId;
        public String merchantName;
        public String merchantRegion;
        public String merchantSegment;
        public double totalAmount;
        public long transactionCount;
    }

    public static final class TransactionTableRow {
        public String id;
        public LocalDateTime createdAt;
        public String merchantName;
        public String merchantRegion;
        public String merchantSegment;
        public String status;
        public String riskBand;
        public double amount;
        public String currency;
        public String paymentMethod;
        public int riskScore;
        public String reviewStatus;
        public String failureCode;
    }

    public static final class ReviewQueueRow {
        public String reviewStatus;
        public String id;
        public LocalDateTime createdAt;
        public String merchantName;
        public String merchantRegion;
        public double amount;
        public int riskScore;
        public String riskBand;
        public int queueRank;
    }

    public static final class TransactionTimelineEvent {
        public String eventType;
        public LocalDateTime createdAt;
        public String actor;
        public String detailText;
    }

    public static final class MerchantTotalReportRow {
        public String merchantName;
        public double totalAmount;
    }

    public static final class RiskBandCountReportRow {
        public String riskBand;
        public long total;
    }

    public static final class RegionCountReportRow {
        public String merchantRegion;
        public long total;
    }

    public static final class RiskTierExposureRow {
        public String merchantRiskTier;
        public double totalAmount;
    }

    public static final class AnalystWorkloadRow {
        public String analyst;
        public String priority;
        public String reviewStatus;
        public String merchantRegion;
        public long reviewCount;
        public double totalExposure;
    }

    public static final class TypedWatchlistRow {
        public String id;
        public String merchantName;
        public String merchantRegion;
        public String reviewStatus;
        public String riskBand;
        public int riskScore;
        public double amount;
    }

    public static final class CancellationProbeRow {
        public String id;
        public String merchantName;
        public String reviewStatus;
        public int riskScore;
    }

    public record MerchantOverviewHeader(String merchantId,
                                         String merchantName,
                                         String segment,
                                         String region,
                                         String riskTier,
                                         long transactionCount,
                                         double totalAmount,
                                         double approvalRate) {
    }

    public record BootstrapPayload(List<String> ranges,
                                   List<String> regions,
                                   List<String> segments,
                                   List<String> statuses,
                                   List<String> riskBands,
                                   List<String> tableColumns,
                                   String defaultRange,
                                   int defaultLimit) {
    }

    public record SummaryCard(String id,
                              String label,
                              String value,
                              String delta,
                              String tone) {
    }

    public record SummaryPayload(String rangeLabel,
                                 int recordCount,
                                 List<SummaryCard> cards) {
    }

    public record ValueMetric(String label,
                              String value,
                              String detail) {
    }

    public record ValueStoryPayload(String title,
                                    String summary,
                                    List<ValueMetric> metrics,
                                    List<String> evidence,
                                    List<String> featureStrip) {
    }

    public record TopMerchantsPayload(List<String> columns,
                                      List<Map<String, Object>> rows,
                                      Map<String, Object> totals,
                                      String source) {
    }

    public record TrendsPayload(ChartJsPayload volumeTrend,
                                ChartJsPayload declineTrend,
                                ChartJsPayload riskBreakdown,
                                ChartJsPayload regionalStatusBreakdown,
                                ChartJsPayload paymentMethodShare,
                                ChartJsPayload declineReasonBreakdown) {
    }

    public record TransactionsPayload(List<String> columns,
                                      List<Map<String, Object>> rows,
                                      int loadedRows,
                                      int totalRows,
                                      int pageSize,
                                      boolean hasMore,
                                      String nextCursor) {
    }

    public record ReviewQueuePayload(List<String> columns,
                                     List<Map<String, Object>> rows,
                                     String source) {
    }

    public record WorkbenchTablePayload(List<String> columns,
                                        List<Map<String, Object>> rows,
                                        Map<String, Object> totals,
                                        String source) {
    }

    public record WorkbenchPayload(WorkbenchTablePayload riskTierStats,
                                   WorkbenchTablePayload analystWorkload,
                                   List<String> computedFields,
                                   Map<String, Object> exposurePolicy,
                                   Map<String, Object> executionGuard,
                                   List<Map<String, Object>> telemetry,
                                   Map<String, Object> joinExplain) {
    }

    public record NaturalQueryStudioPayload(String queryText,
                                            List<String> columns,
                                            List<Map<String, Object>> rows,
                                            ChartJsPayload chart,
                                            Object schema,
                                            Map<String, Object> explain) {
    }

    public record TypedQueryStudioPayload(List<String> columns,
                                          List<Map<String, Object>> rows,
                                          Object schema,
                                          Map<String, Object> explain) {
    }

    public record CancellationDemoPayload(String queryText,
                                          boolean cancelled,
                                          String blockCode,
                                          Integer rowsReturnedBeforeAbort,
                                          List<String> columns,
                                          List<Map<String, Object>> rows,
                                          Map<String, Object> executionGuard) {
    }

    public record QueryStudioPayload(NaturalQueryStudioPayload naturalQuery,
                                     TypedQueryStudioPayload typedQuery,
                                     CancellationDemoPayload cancellationDemo) {
    }

    public record MerchantOverviewPayload(MerchantOverviewHeader header,
                                          ChartJsPayload volumeTrend,
                                          List<String> columns,
                                          List<Map<String, Object>> rows) {
    }

    public record TransactionDetailHeader(String id,
                                          LocalDateTime createdAt,
                                          String merchantId,
                                          String merchantName,
                                          String merchantRegion,
                                          String customerId,
                                          String customerCountry,
                                          double amount,
                                          String currency,
                                          String status,
                                          String riskBand,
                                          int riskScore,
                                          String paymentMethod,
                                          String cardBrand,
                                          String reviewStatus,
                                          String reviewPriority,
                                          String reviewReasonCode,
                                          String chargebackStage,
                                          String failureCode) {
    }

    public record TransactionDetailPayload(TransactionDetailHeader header,
                                           List<String> eventColumns,
                                           List<Map<String, Object>> events) {
    }

    public record ReportCatalogItem(String id,
                                    String name,
                                    String kind,
                                    String summary,
                                    String category,
                                    Map<String, Object> defaultParams,
                                    boolean chartEnabled) {
    }

    public record ReportRunPayload(String reportId,
                                   String name,
                                   List<String> columns,
                                   List<Map<String, Object>> rows,
                                   ChartJsPayload chart,
                                   String source) {
    }

    public record ReportInspectPayload(String reportId,
                                       String name,
                                       String queryText,
                                       Map<String, Object> defaultParams,
                                       List<String> schema,
                                       Object planPreview,
                                       Object diagnostics,
                                       Object pushdownPreview,
                                       Map<String, Object> explain) {
    }
}
