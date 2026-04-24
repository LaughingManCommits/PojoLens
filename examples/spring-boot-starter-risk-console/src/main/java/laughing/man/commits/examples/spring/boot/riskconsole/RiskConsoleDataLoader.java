package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.ChargebackCase;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.CustomerAccount;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.Merchant;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.PaymentTransaction;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.RiskReview;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Component
class RiskConsoleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RiskConsoleDataLoader.class);

    private static final String[] REGIONS = {"NA", "EU", "APAC", "LATAM"};
    private static final String[] SEGMENTS = {"Enterprise", "Mid-Market", "SMB", "Platform"};
    private static final String[] RISK_TIERS = {"LOW", "MEDIUM", "HIGH"};
    private static final String[] OWNER_TEAMS = {"Risk Ops", "Merchant Success", "Payments", "Strategic"};
    private static final String[] COUNTRIES = {"US", "CA", "GB", "DE", "FR", "NL", "SG", "AU", "BR", "MX", "JP"};
    private static final String[] KYC = {"VERIFIED", "PENDING", "REVIEW"};
    private static final String[] PAYMENT_METHODS = {"CARD", "WALLET", "BANK_TRANSFER", "BUY_NOW_PAY_LATER"};
    private static final String[] CARD_BRANDS = {"VISA", "MASTERCARD", "AMEX", "MAESTRO"};
    private static final String[] DECLINE_CODES = {"DO_NOT_HONOR", "INSUFFICIENT_FUNDS", "AVS_FAIL", "CVV_FAIL", "FRAUD_RULE"};
    private static final String[] REVIEW_REASONS = {"DEVICE_MISMATCH", "HIGH_VELOCITY", "IP_RISK", "GEO_MISMATCH"};
    private static final String[] ANALYSTS = {"Ava", "Mika", "Jon", "Rui", "Nina", "Leo"};
    private static final String[] CHARGEBACK_REASONS = {"FRAUD", "PRODUCT_NOT_RECEIVED", "DUPLICATE", "SUBSCRIPTION"};
    private static final String[] CHARGEBACK_STAGES = {"EVIDENCE_DUE", "ARBITRATION", "CLOSED"};
    private static final String[] MERCHANT_PREFIX = {"NorthPeak", "Atlas", "River", "Nova", "Signal", "Harbor", "Summit"};
    private static final String[] MERCHANT_SUFFIX = {"Retail", "Travel", "Digital", "Market", "Health", "Logistics"};

    private final RiskConsoleJdbcRepository repository;
    private final RiskConsoleSeedProperties properties;

    RiskConsoleDataLoader(RiskConsoleJdbcRepository repository, RiskConsoleSeedProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Risk console seed disabled");
            return;
        }
        if (repository.hasSeedData()) {
            log.info("Risk console seed skipped. Data already present.");
            return;
        }

        Random random = new Random(properties.getSeed());
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        List<Merchant> merchants = buildMerchants(random, now);
        List<CustomerAccount> customers = buildCustomers(random, now);
        GeneratedData generated = buildTransactions(merchants, customers, random, now);

        repository.insertMerchants(merchants);
        repository.insertCustomers(customers);
        repository.insertTransactions(generated.transactions());
        if (!generated.reviews().isEmpty()) {
            repository.insertReviews(generated.reviews());
        }
        if (!generated.chargebacks().isEmpty()) {
            repository.insertChargebacks(generated.chargebacks());
        }
        if (!generated.events().isEmpty()) {
            repository.insertEvents(generated.events());
        }

        log.info("Risk console seed complete. merchants={}, customers={}, transactions={}, reviews={}, chargebacks={}, events={}",
                merchants.size(), customers.size(), generated.transactions().size(), generated.reviews().size(),
                generated.chargebacks().size(), generated.events().size());
    }

    private List<Merchant> buildMerchants(Random random, LocalDateTime now) {
        List<Merchant> merchants = new ArrayList<>(properties.getMerchantCount());
        for (int i = 1; i <= properties.getMerchantCount(); i++) {
            Merchant merchant = new Merchant();
            merchant.id = "M-" + format(i, 4);
            merchant.region = REGIONS[i % REGIONS.length];
            merchant.segment = SEGMENTS[(i + 1) % SEGMENTS.length];
            merchant.riskTier = RISK_TIERS[(i + random.nextInt(RISK_TIERS.length)) % RISK_TIERS.length];
            merchant.ownerTeam = OWNER_TEAMS[random.nextInt(OWNER_TEAMS.length)];
            merchant.name = MERCHANT_PREFIX[i % MERCHANT_PREFIX.length] + " "
                    + MERCHANT_SUFFIX[random.nextInt(MERCHANT_SUFFIX.length)] + " " + format(i, 3);
            merchant.onboardedAt = now.minusDays(60L + random.nextInt(720));
            merchant.active = random.nextDouble() > 0.02D;
            merchants.add(merchant);
        }
        return merchants;
    }

    private List<CustomerAccount> buildCustomers(Random random, LocalDateTime now) {
        List<CustomerAccount> customers = new ArrayList<>(properties.getCustomerCount());
        for (int i = 1; i <= properties.getCustomerCount(); i++) {
            CustomerAccount customer = new CustomerAccount();
            customer.id = "C-" + format(i, 6);
            customer.country = COUNTRIES[random.nextInt(COUNTRIES.length)];
            customer.emailHash = "cust_" + format(i, 6) + "_" + Integer.toHexString(10_000 + i);
            customer.signupAt = now.minusDays(10L + random.nextInt(1000));
            customer.kycStatus = KYC[random.nextInt(KYC.length)];
            customer.lifetimeValue = roundMoney(100D + (random.nextDouble() * 12_500D));
            customer.chargebackCount = 0;
            customers.add(customer);
        }
        return customers;
    }

    private GeneratedData buildTransactions(List<Merchant> merchants,
                                            List<CustomerAccount> customers,
                                            Random random,
                                            LocalDateTime now) {
        List<PaymentTransaction> transactions = new ArrayList<>(properties.getTransactionCount());
        List<RiskReview> reviews = new ArrayList<>();
        List<ChargebackCase> chargebacks = new ArrayList<>();
        List<TransactionEvent> events = new ArrayList<>(properties.getTransactionCount() * 2);
        Map<String, Integer> chargebacksByCustomer = new ConcurrentHashMap<>();

        for (int i = 1; i <= properties.getTransactionCount(); i++) {
            Merchant merchant = merchants.get(random.nextInt(merchants.size()));
            CustomerAccount customer = customers.get(random.nextInt(customers.size()));
            LocalDateTime createdAt = randomTimestamp(random, now);
            int riskScore = riskScore(random, merchant, createdAt, i);
            String riskBand = riskBand(riskScore);
            String status = status(random, riskScore, createdAt, merchant);

            PaymentTransaction transaction = new PaymentTransaction();
            transaction.id = "TX-" + createdAt.getYear() + format(i, 7);
            transaction.merchantId = merchant.id;
            transaction.customerId = customer.id;
            transaction.createdAt = createdAt;
            transaction.amount = amount(random, merchant.segment);
            transaction.currency = "USD";
            transaction.paymentMethod = PAYMENT_METHODS[random.nextInt(PAYMENT_METHODS.length)];
            transaction.cardBrand = CARD_BRANDS[random.nextInt(CARD_BRANDS.length)];
            transaction.status = status;
            transaction.riskScore = riskScore;
            transaction.riskBand = riskBand;
            transaction.failureCode = "DECLINED".equals(status)
                    ? DECLINE_CODES[random.nextInt(DECLINE_CODES.length)]
                    : null;
            transactions.add(transaction);

            events.add(event(transaction.id, "AUTH_RECEIVED", createdAt, "gateway", "Authorization request received"));
            events.add(event(transaction.id, statusEvent(status), createdAt.plusMinutes(1), "decision-engine",
                    "Decision set to " + status.toLowerCase(Locale.ROOT)));

            if ("REVIEW".equals(status) || riskScore >= 88 || (riskScore >= 78 && random.nextDouble() < 0.08D)) {
                RiskReview review = review(random, transaction, createdAt);
                reviews.add(review);
                events.add(event(transaction.id, "REVIEW_" + review.reviewStatus,
                        createdAt.plusMinutes(3), "risk-ops", "Review " + review.reviewStatus.toLowerCase(Locale.ROOT)));
            }

            if (shouldCreateChargeback(random, transaction, createdAt)) {
                ChargebackCase chargeback = chargeback(random, transaction, createdAt);
                chargebacks.add(chargeback);
                chargebacksByCustomer.merge(customer.id, 1, Integer::sum);
                events.add(event(transaction.id, "CHARGEBACK_OPENED", chargeback.openedAt, "issuer",
                        "Chargeback opened for " + chargeback.reasonCode.toLowerCase(Locale.ROOT)));
            }
        }

        customers.forEach(customer -> customer.chargebackCount = chargebacksByCustomer.getOrDefault(customer.id, 0));
        return new GeneratedData(transactions, reviews, chargebacks, events);
    }

    private LocalDateTime randomTimestamp(Random random, LocalDateTime now) {
        double recentBias = Math.pow(random.nextDouble(), 1.8D);
        long minutesBack = (long) (recentBias * 525_600D);
        return now.minusMinutes(minutesBack);
    }

    private int riskScore(Random random, Merchant merchant, LocalDateTime createdAt, int index) {
        int base = switch (merchant.riskTier) {
            case "HIGH" -> 68;
            case "MEDIUM" -> 44;
            default -> 22;
        };
        if ("LATAM".equals(merchant.region) || "APAC".equals(merchant.region)) {
            base += 7;
        }
        if (createdAt.isAfter(LocalDateTime.now().minusDays(14)) && index % 37 == 0) {
            base += 18;
        }
        return Math.max(5, Math.min(99, base + random.nextInt(35)));
    }

    private String riskBand(int riskScore) {
        if (riskScore >= 85) {
            return "CRITICAL";
        }
        if (riskScore >= 65) {
            return "HIGH";
        }
        if (riskScore >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String status(Random random, int riskScore, LocalDateTime createdAt, Merchant merchant) {
        double roll = random.nextDouble();
        if (!merchant.active) {
            return "DECLINED";
        }
        if (riskScore >= 85) {
            if (roll < 0.22D) {
                return "REVIEW";
            }
            if (roll < 0.58D) {
                return "DECLINED";
            }
            if (roll < 0.92D) {
                return "APPROVED";
            }
            return createdAt.isBefore(LocalDateTime.now().minusDays(20)) ? "CHARGEBACK" : "APPROVED";
        }
        if (riskScore >= 65) {
            if (roll < 0.09D) {
                return "REVIEW";
            }
            if (roll < 0.24D) {
                return "DECLINED";
            }
            if (roll < 0.97D) {
                return "APPROVED";
            }
            return "REFUNDED";
        }
        if (roll < 0.05D) {
            return "DECLINED";
        }
        if (roll < 0.97D) {
            return "APPROVED";
        }
        return "REFUNDED";
    }

    private double amount(Random random, String segment) {
        double base = switch (segment) {
            case "Enterprise" -> 650D;
            case "Mid-Market" -> 280D;
            case "Platform" -> 145D;
            default -> 88D;
        };
        return roundMoney(base + (random.nextDouble() * base * 3.2D));
    }

    private RiskReview review(Random random, PaymentTransaction transaction, LocalDateTime createdAt) {
        RiskReview review = new RiskReview();
        review.id = "RV-" + transaction.id;
        review.transactionId = transaction.id;
        review.queueName = transaction.riskScore >= 85 ? "MANUAL_REVIEW" : "WATCHLIST";
        review.priority = transaction.riskScore >= 90 ? "HIGH" : transaction.riskScore >= 75 ? "MEDIUM" : "LOW";
        review.reasonCode = REVIEW_REASONS[random.nextInt(REVIEW_REASONS.length)];
        review.analyst = ANALYSTS[random.nextInt(ANALYSTS.length)];
        review.openedAt = createdAt.plusMinutes(2);
        if (random.nextDouble() < 0.45D) {
            review.reviewStatus = random.nextDouble() < 0.25D ? "ESCALATED" : "OPEN";
            review.resolvedAt = null;
            review.resolution = null;
        } else {
            review.reviewStatus = "CLOSED";
            review.resolvedAt = review.openedAt.plusHours(1 + random.nextInt(72));
            review.resolution = random.nextDouble() < 0.7D ? "APPROVED" : "DECLINED";
        }
        return review;
    }

    private boolean shouldCreateChargeback(Random random, PaymentTransaction transaction, LocalDateTime createdAt) {
        if (createdAt.isAfter(LocalDateTime.now().minusDays(20))) {
            return false;
        }
        if ("CHARGEBACK".equals(transaction.status)) {
            return true;
        }
        if (!"APPROVED".equals(transaction.status) && !"REFUNDED".equals(transaction.status)) {
            return false;
        }
        double threshold = transaction.riskScore >= 85 ? 0.06D : transaction.riskScore >= 65 ? 0.025D : 0.007D;
        return random.nextDouble() < threshold;
    }

    private ChargebackCase chargeback(Random random, PaymentTransaction transaction, LocalDateTime createdAt) {
        ChargebackCase chargeback = new ChargebackCase();
        chargeback.id = "CB-" + transaction.id;
        chargeback.transactionId = transaction.id;
        chargeback.merchantId = transaction.merchantId;
        chargeback.reasonCode = CHARGEBACK_REASONS[random.nextInt(CHARGEBACK_REASONS.length)];
        chargeback.stage = CHARGEBACK_STAGES[random.nextInt(CHARGEBACK_STAGES.length)];
        chargeback.amount = transaction.amount;
        chargeback.openedAt = createdAt.plusDays(12 + random.nextInt(18));
        chargeback.dueAt = chargeback.openedAt.plusDays(7 + random.nextInt(9));
        chargeback.outcome = "CLOSED".equals(chargeback.stage) ? (random.nextBoolean() ? "WON" : "LOST") : null;
        return chargeback;
    }

    private TransactionEvent event(String transactionId,
                                   String eventType,
                                   LocalDateTime createdAt,
                                   String actor,
                                   String detail) {
        TransactionEvent event = new TransactionEvent();
        event.id = transactionId + "-" + eventType;
        event.transactionId = transactionId;
        event.eventType = eventType;
        event.createdAt = createdAt;
        event.actor = actor;
        event.detailText = detail;
        return event;
    }

    private String statusEvent(String status) {
        return switch (status) {
            case "APPROVED" -> "AUTH_APPROVED";
            case "DECLINED" -> "AUTH_DECLINED";
            case "REVIEW" -> "REVIEW_REQUIRED";
            case "REFUNDED" -> "REFUND_CREATED";
            case "CHARGEBACK" -> "AUTH_APPROVED";
            default -> "STATUS_UPDATED";
        };
    }

    private double roundMoney(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private String format(int value, int width) {
        return String.format(Locale.ROOT, "%0" + width + "d", value);
    }

    private record GeneratedData(List<PaymentTransaction> transactions,
                                 List<RiskReview> reviews,
                                 List<ChargebackCase> chargebacks,
                                 List<TransactionEvent> events) {
    }
}
