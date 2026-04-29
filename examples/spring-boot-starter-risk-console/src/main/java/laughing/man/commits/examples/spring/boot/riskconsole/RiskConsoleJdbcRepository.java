package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ChargebackCase;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.CustomerAccount;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.Merchant;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.PaymentTransaction;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.RiskReview;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionEvent;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionDetailHeader;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionRecord;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionTimelineEvent;
import laughing.man.commits.spring.boot.autoconfigure.PojoLensJdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
class RiskConsoleJdbcRepository {

    private static final String TRANSACTION_SNAPSHOT_SQL = """
            select t.id,
                   t.merchant_id as merchant_id,
                   m.name as merchant_name,
                   m.segment as merchant_segment,
                   m.region as merchant_region,
                   m.risk_tier as merchant_risk_tier,
                   t.customer_id as customer_id,
                   c.country as customer_country,
                   t.created_at as created_at,
                   t.amount,
                   t.currency,
                   t.payment_method as payment_method,
                   t.card_brand as card_brand,
                   t.status,
                   t.risk_score as risk_score,
                   t.risk_band as risk_band,
                   t.failure_code as failure_code,
                   coalesce(r.review_status, 'NONE') as review_status,
                   case when cb.id is null then false else true end as chargeback_open,
                   coalesce(cb.stage, 'NONE') as chargeback_stage
            from payment_transactions t
            join merchants m on m.id = t.merchant_id
            join customer_accounts c on c.id = t.customer_id
            left join risk_reviews r on r.transaction_id = t.id
            left join chargeback_cases cb on cb.transaction_id = t.id and cb.stage not in ('WON', 'LOST', 'CLOSED')
            where t.created_at >= ? and t.created_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;

    RiskConsoleJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean hasSeedData() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from merchants", Integer.class);
        return count != null && count > 0;
    }

    List<String> distinctMerchantRegions() {
        return jdbcTemplate.queryForList("select distinct region from merchants order by region", String.class);
    }

    List<String> distinctMerchantSegments() {
        return jdbcTemplate.queryForList("select distinct segment from merchants order by segment", String.class);
    }

    List<String> distinctStatuses() {
        return jdbcTemplate.queryForList("select distinct status from payment_transactions order by status", String.class);
    }

    List<String> distinctRiskBands() {
        return jdbcTemplate.queryForList("select distinct risk_band from payment_transactions order by risk_band", String.class);
    }

    Merchant loadMerchant(String merchantId) {
        List<Merchant> merchants = queryRows(
                "select id, name, segment, region, risk_tier, owner_team, onboarded_at, active from merchants where id = ?",
                Merchant.class,
                merchantId
        );
        return merchants.isEmpty() ? null : merchants.get(0);
    }

    List<TransactionRecord> loadTransactionRecords(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        List<TransactionRecord> rows = queryRows(
                TRANSACTION_SNAPSHOT_SQL,
                TransactionRecord.class,
                Timestamp.valueOf(startInclusive),
                Timestamp.valueOf(endExclusive)
        );
        return enrichTransactionRecords(rows);
    }

    List<TransactionRecord> loadMerchantTransactionRecords(String merchantId,
                                                           LocalDateTime startInclusive,
                                                           LocalDateTime endExclusive) {
        List<TransactionRecord> rows = queryRows(
                TRANSACTION_SNAPSHOT_SQL + " and t.merchant_id = ?",
                TransactionRecord.class,
                Timestamp.valueOf(startInclusive),
                Timestamp.valueOf(endExclusive),
                merchantId
        );
        return enrichTransactionRecords(rows);
    }

    List<PaymentTransaction> loadPaymentTransactions(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return queryRows(
                """
                        select id,
                               merchant_id,
                               customer_id,
                               created_at,
                               amount,
                               currency,
                               payment_method,
                               card_brand,
                               status,
                               risk_score,
                               risk_band,
                               failure_code
                        from payment_transactions
                        where created_at >= ? and created_at < ?
                        """,
                PaymentTransaction.class,
                Timestamp.valueOf(startInclusive),
                Timestamp.valueOf(endExclusive)
        );
    }

    List<RiskReview> loadRiskReviews() {
        return queryRows(
                """
                        select id,
                               transaction_id,
                               queue_name,
                               priority,
                               reason_code,
                               analyst,
                               opened_at,
                               resolved_at,
                               review_status,
                               resolution
                        from risk_reviews
                        """,
                RiskReview.class
        );
    }

    List<Merchant> loadMerchants() {
        return queryRows(
                "select id, name, segment, region, risk_tier, owner_team, onboarded_at, active from merchants",
                Merchant.class
        );
    }

    TransactionDetailHeader loadTransactionDetail(String transactionId) {
        List<TransactionDetailHeader> rows = jdbcTemplate.query(
                """
                        select t.id,
                               t.created_at as createdAt,
                               t.merchant_id as merchantId,
                               m.name as merchantName,
                               m.region as merchantRegion,
                               t.customer_id as customerId,
                               c.country as customerCountry,
                               t.amount,
                               t.currency,
                               t.status,
                               t.risk_band as riskBand,
                               t.risk_score as riskScore,
                               t.payment_method as paymentMethod,
                               t.card_brand as cardBrand,
                               coalesce(r.review_status, 'NONE') as reviewStatus,
                               coalesce(r.priority, 'NONE') as reviewPriority,
                               coalesce(r.reason_code, 'NONE') as reviewReasonCode,
                               coalesce(cb.stage, 'NONE') as chargebackStage,
                               t.failure_code as failureCode
                        from payment_transactions t
                        join merchants m on m.id = t.merchant_id
                        join customer_accounts c on c.id = t.customer_id
                        left join risk_reviews r on r.transaction_id = t.id
                        left join chargeback_cases cb on cb.transaction_id = t.id
                        where t.id = ?
                        """,
                (rs, rowNum) -> new TransactionDetailHeader(
                        rs.getString("id"),
                        rs.getTimestamp("createdAt").toLocalDateTime(),
                        rs.getString("merchantId"),
                        rs.getString("merchantName"),
                        rs.getString("merchantRegion"),
                        rs.getString("customerId"),
                        rs.getString("customerCountry"),
                        rs.getDouble("amount"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getString("riskBand"),
                        rs.getInt("riskScore"),
                        rs.getString("paymentMethod"),
                        rs.getString("cardBrand"),
                        rs.getString("reviewStatus"),
                        rs.getString("reviewPriority"),
                        rs.getString("reviewReasonCode"),
                        rs.getString("chargebackStage"),
                        rs.getString("failureCode")
                ),
                transactionId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    List<TransactionTimelineEvent> loadTransactionTimeline(String transactionId) {
        return queryRows(
                """
                        select event_type,
                               created_at,
                               actor,
                               detail_text
                        from transaction_events
                        where transaction_id = ?
                        order by created_at asc, id asc
                        """,
                TransactionTimelineEvent.class,
                transactionId
        );
    }

    void insertMerchants(List<Merchant> merchants) {
        jdbcTemplate.batchUpdate(
                "insert into merchants (id, name, segment, region, risk_tier, owner_team, onboarded_at, active) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                merchants,
                250,
                (ps, merchant) -> {
                    ps.setString(1, merchant.id);
                    ps.setString(2, merchant.name);
                    ps.setString(3, merchant.segment);
                    ps.setString(4, merchant.region);
                    ps.setString(5, merchant.riskTier);
                    ps.setString(6, merchant.ownerTeam);
                    ps.setTimestamp(7, Timestamp.valueOf(merchant.onboardedAt));
                    ps.setBoolean(8, merchant.active);
                }
        );
    }

    void insertCustomers(List<CustomerAccount> customers) {
        jdbcTemplate.batchUpdate(
                "insert into customer_accounts (id, email_hash, country, signup_at, kyc_status, lifetime_value, chargeback_count) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                customers,
                500,
                (ps, customer) -> {
                    ps.setString(1, customer.id);
                    ps.setString(2, customer.emailHash);
                    ps.setString(3, customer.country);
                    ps.setTimestamp(4, Timestamp.valueOf(customer.signupAt));
                    ps.setString(5, customer.kycStatus);
                    ps.setDouble(6, customer.lifetimeValue);
                    ps.setInt(7, customer.chargebackCount);
                }
        );
    }

    void insertTransactions(List<PaymentTransaction> transactions) {
        jdbcTemplate.batchUpdate(
                "insert into payment_transactions "
                        + "(id, merchant_id, customer_id, created_at, amount, currency, payment_method, card_brand, status, risk_score, risk_band, failure_code) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                transactions,
                500,
                (ps, transaction) -> {
                    ps.setString(1, transaction.id);
                    ps.setString(2, transaction.merchantId);
                    ps.setString(3, transaction.customerId);
                    ps.setTimestamp(4, Timestamp.valueOf(transaction.createdAt));
                    ps.setDouble(5, transaction.amount);
                    ps.setString(6, transaction.currency);
                    ps.setString(7, transaction.paymentMethod);
                    ps.setString(8, transaction.cardBrand);
                    ps.setString(9, transaction.status);
                    ps.setInt(10, transaction.riskScore);
                    ps.setString(11, transaction.riskBand);
                    ps.setString(12, transaction.failureCode);
                }
        );
    }

    void insertReviews(List<RiskReview> reviews) {
        jdbcTemplate.batchUpdate(
                "insert into risk_reviews "
                        + "(id, transaction_id, queue_name, priority, reason_code, analyst, opened_at, resolved_at, review_status, resolution) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                reviews,
                250,
                (ps, review) -> {
                    ps.setString(1, review.id);
                    ps.setString(2, review.transactionId);
                    ps.setString(3, review.queueName);
                    ps.setString(4, review.priority);
                    ps.setString(5, review.reasonCode);
                    ps.setString(6, review.analyst);
                    ps.setTimestamp(7, Timestamp.valueOf(review.openedAt));
                    if (review.resolvedAt == null) {
                        ps.setTimestamp(8, null);
                    } else {
                        ps.setTimestamp(8, Timestamp.valueOf(review.resolvedAt));
                    }
                    ps.setString(9, review.reviewStatus);
                    ps.setString(10, review.resolution);
                }
        );
    }

    void insertChargebacks(List<ChargebackCase> chargebacks) {
        jdbcTemplate.batchUpdate(
                "insert into chargeback_cases "
                        + "(id, transaction_id, merchant_id, reason_code, stage, amount, opened_at, due_at, outcome) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                chargebacks,
                250,
                (ps, chargeback) -> {
                    ps.setString(1, chargeback.id);
                    ps.setString(2, chargeback.transactionId);
                    ps.setString(3, chargeback.merchantId);
                    ps.setString(4, chargeback.reasonCode);
                    ps.setString(5, chargeback.stage);
                    ps.setDouble(6, chargeback.amount);
                    ps.setTimestamp(7, Timestamp.valueOf(chargeback.openedAt));
                    ps.setTimestamp(8, Timestamp.valueOf(chargeback.dueAt));
                    ps.setString(9, chargeback.outcome);
                }
        );
    }

    void insertEvents(List<TransactionEvent> events) {
        jdbcTemplate.batchUpdate(
                "insert into transaction_events (id, transaction_id, event_type, created_at, actor, detail_text) "
                        + "values (?, ?, ?, ?, ?, ?)",
                events,
                750,
                (ps, event) -> {
                    ps.setString(1, event.id);
                    ps.setString(2, event.transactionId);
                    ps.setString(3, event.eventType);
                    ps.setTimestamp(4, Timestamp.valueOf(event.createdAt));
                    ps.setString(5, event.actor);
                    ps.setString(6, event.detailText);
                }
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> queryRows(String sql, Class<T> rowClass, Object... args) {
        return PojoLensJdbc.query(jdbcTemplate, sql, rowClass, args);
    }

    private List<TransactionRecord> enrichTransactionRecords(List<TransactionRecord> rows) {
        for (TransactionRecord row : rows) {
            row.searchText = String.join(" ",
                    safe(row.id),
                    safe(row.merchantName),
                    safe(row.customerCountry),
                    safe(row.failureCode),
                    safe(row.status),
                    safe(row.riskBand)).toLowerCase();
        }
        return rows;
    }
}
