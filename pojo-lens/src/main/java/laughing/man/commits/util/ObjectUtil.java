package laughing.man.commits.util;

import laughing.man.commits.EngineDefaults;
import laughing.man.commits.enums.Clauses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * High-performance value casting and comparison helpers used by the filtering engine.
 *
 * Important:
 * Date comparisons preserve old semantics:
 * values are normalized according to the provided format before comparison.
 */
public final class ObjectUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectUtil.class);

    private static final String DEFAULT_DATE_FORMAT = EngineDefaults.SDF;
    private static final int DATE_PLAN_CACHE_MAX_ENTRIES = 16;
    private static final int REGEX_CACHE_MAX_ENTRIES = 64;

    /**
     * These are bounded internal memoization helpers, not part of the public runtime cache surface.
     */
    private static final BoundedCache<String, DateFormatPlan> DATE_PLAN_CACHE =
            new BoundedCache<>(DATE_PLAN_CACHE_MAX_ENTRIES);
    private static final BoundedCache<String, Pattern> REGEX_CACHE =
            new BoundedCache<>(REGEX_CACHE_MAX_ENTRIES);

    private ObjectUtil() {
    }

    public static boolean compareObject(Object fieldValue,
                                        Object compareObject,
                                        Clauses clause,
                                        String dateFormat) {
        if (clause == null) {
            return false;
        }

        if (compareObject == null) {
            return switch (clause) {
                case EQUAL -> fieldValue == null;
                case NOT_EQUAL -> fieldValue != null;
                default -> false;
            };
        }

        final boolean negatedSetClause = isNegatedSetClause(clause);

        if (compareObject instanceof Map<?, ?> map) {
            return evaluateIterableComparison(fieldValue, map.values(), clause, dateFormat, negatedSetClause);
        }
        if (compareObject instanceof Collection<?> collection) {
            return evaluateIterableComparison(fieldValue, collection, clause, dateFormat, negatedSetClause);
        }
        if (compareObject instanceof Iterable<?> iterable) {
            return evaluateIterableComparison(fieldValue, iterable, clause, dateFormat, negatedSetClause);
        }
        if (compareObject.getClass().isArray()) {
            return evaluateArrayComparison(fieldValue, compareObject, clause, dateFormat, negatedSetClause);
        }
        return compare(fieldValue, compareObject, clause, dateFormat);
    }

    public static String castToString(Object fieldValue) {
        return castToString(fieldValue, null);
    }

    public static String castToString(Object fieldValue, String dateFormat) {
        if (fieldValue == null) {
            return String.valueOf((Object) null);
        }
        try {
            DateFormatPlan plan = datePlan(dateFormat);
            return switch (fieldValue) {
                case Date date -> plan.format(date.toInstant());
                case Instant instant -> plan.format(instant);
                case ZonedDateTime zdt -> plan.format(zdt.toInstant());
                case OffsetDateTime odt -> plan.format(odt.toInstant());
                case LocalDateTime ldt -> plan.formatter().format(ldt);
                case LocalDate ld -> plan.formatter().format(ld);
                default -> String.valueOf(fieldValue);
            };
        } catch (Exception e) {
            LOG.error("Failed to cast field [{}]", fieldValue, e);
            return null;
        }
    }

    public static <T> T value(Object value, Class<T> cls) {
        return cls.cast(value);
    }

    public static <T> T castValue(Object fieldValue, Class<T> cls) {
        try {
            if (fieldValue == null || cls == null) {
                return null;
            }

            if (cls.isInstance(fieldValue)) {
                return cls.cast(fieldValue);
            }

            if (cls == String.class) {
                return cls.cast(castToString(fieldValue, DEFAULT_DATE_FORMAT));
            }

            if (cls == Integer.class) {
                if (fieldValue instanceof Number n) {
                    return cls.cast(Integer.valueOf(n.intValue()));
                }
                String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
                return s == null ? null : cls.cast(parseIntegerCompatible(s));
            }

            if (cls == Long.class) {
                if (fieldValue instanceof Number n) {
                    return cls.cast(Long.valueOf(n.longValue()));
                }
                String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
                return s == null ? null : cls.cast(parseLongCompatible(s));
            }

            if (cls == Double.class) {
                if (fieldValue instanceof Number n) {
                    return cls.cast(Double.valueOf(n.doubleValue()));
                }
                String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
                return s == null ? null : cls.cast(Double.valueOf(s));
            }

            if (cls == Float.class) {
                if (fieldValue instanceof Number n) {
                    return cls.cast(Float.valueOf(n.floatValue()));
                }
                String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
                return s == null ? null : cls.cast(Float.valueOf(s));
            }

            if (cls == Short.class) {
                if (fieldValue instanceof Number n) {
                    return cls.cast(Short.valueOf(n.shortValue()));
                }
                String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
                return s == null ? null : cls.cast(Short.valueOf(parseIntegerCompatible(s).shortValue()));
            }

            if (cls == Byte.class) {
                if (fieldValue instanceof Number n) {
                    return cls.cast(Byte.valueOf(n.byteValue()));
                }
                String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
                return s == null ? null : cls.cast(Byte.valueOf(parseIntegerCompatible(s).byteValue()));
            }

            if (cls == Character.class) {
                String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
                return s == null || s.length() != 1 ? null : cls.cast(s.charAt(0));
            }

            if (cls == BigDecimal.class) {
                if (fieldValue instanceof Number n) {
                    return cls.cast(toBigDecimal(n));
                }
                return cls.cast(StringUtil.parseNumber(castToString(fieldValue, DEFAULT_DATE_FORMAT)));
            }

            if (cls == BigInteger.class) {
                if (fieldValue instanceof BigDecimal d) {
                    return cls.cast(d.toBigInteger());
                }
                if (fieldValue instanceof Number n && isIntegral(n)) {
                    return cls.cast(BigInteger.valueOf(n.longValue()));
                }
                BigDecimal parsed = StringUtil.parseNumber(castToString(fieldValue, DEFAULT_DATE_FORMAT));
                return parsed == null ? null : cls.cast(parsed.toBigInteger());
            }

            if (cls == Boolean.class) {
                if (fieldValue instanceof Boolean b) {
                    return cls.cast(b);
                }
                String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
                Boolean parsed = StringUtil.parseBoolStrict(s);
                return parsed == null ? null : cls.cast(parsed);
            }

            if (cls == Date.class) {
                Long millis = normalizeToEpochMillis(fieldValue, DEFAULT_DATE_FORMAT);
                return millis == null ? null : cls.cast(new Date(millis));
            }

            String s = castToString(fieldValue, DEFAULT_DATE_FORMAT);
            return s == null ? null : value(s, cls);

        } catch (Exception e) {
            String targetType = cls.getSimpleName();
            LOG.error("Cannot cast value [{}] to type [{}]",
                    fieldValue,
                    targetType,
                    e);
            return null;
        }
    }

    private static boolean compare(Object fieldValue,
                                   Object compareValue,
                                   Clauses clause,
                                   String dateFormat) {
        if (fieldValue == null || clause == null) {
            return false;
        }
        if (compareValue == null) {
            // A null element of an IN list or subquery result never matches, so only the
            // negated equality and text clauses hold for a non-null field.
            return clause == Clauses.NOT_EQUAL
                    || clause == Clauses.NOT_CONTAINS
                    || clause == Clauses.NOT_MATCHES;
        }

        try {
            if (fieldValue instanceof Number n) {
                return compareNumberValues(n, compareValue, clause);
            }
            if (fieldValue instanceof Boolean b) {
                return compareBooleanValues(b, compareValue, clause);
            }
            if (fieldValue instanceof String s) {
                return compareStringValues(s, compareValue, clause, dateFormat);
            }
            if (fieldValue instanceof Character c) {
                return compareStringValues(String.valueOf(c), compareValue, clause, dateFormat);
            }
            if (fieldValue instanceof Enum<?> e) {
                return compareEnumValues(e, compareValue, clause, dateFormat);
            }
            if (isDateLike(fieldValue)) {
                return compareDateValues(fieldValue, compareValue, clause, dateFormat);
            }
            return compareOtherValues(fieldValue, compareValue, clause, dateFormat);
        } catch (Exception e) {
            LOG.error("Failed to compare field [{}] with field [{}] with clause [{}]",
                    fieldValue, compareValue, clause, e);
        }

        return false;
    }

    private static DateFormatPlan datePlan(String dateFormat) {
        final String effective = (dateFormat == null || dateFormat.isEmpty())
                ? DEFAULT_DATE_FORMAT
                : dateFormat;
        return DATE_PLAN_CACHE.getOrCompute(effective, DateFormatPlan::create);
    }

    private static Pattern regexPattern(String regex) {
        return REGEX_CACHE.getOrCompute(regex, Pattern::compile);
    }

    private static ZoneId systemZone() {
        return ZoneId.systemDefault();
    }

    static void clearInternalCaches() {
        DATE_PLAN_CACHE.clear();
        REGEX_CACHE.clear();
    }

    static int internalDatePlanCacheSize() {
        return DATE_PLAN_CACHE.size();
    }

    static int internalRegexCacheSize() {
        return REGEX_CACHE.size();
    }

    private static boolean compareNumberValues(Number fieldValue,
                                               Object compareValue,
                                               Clauses clause) {
        if (compareValue instanceof Number n) {
            return compareNumbers(fieldValue, n, clause);
        }

        final String s = (compareValue instanceof String str) ? str : castToString(compareValue);
        final BigDecimal parsed = StringUtil.parseNumber(s);
        if (parsed != null) {
            return compareNumbers(fieldValue, parsed, clause);
        }

        if (LOG.isWarnEnabled()) {
            LOG.warn("compareValue [{}] type [{}] is not a convertible numeric type",
                    compareValue, compareValue.getClass().getSimpleName());
        }
        return false;
    }

    /**
     * Evaluates a numeric predicate without losing precision: integral pairs compare as
     * {@code long}, pairs involving {@code BigDecimal}/{@code BigInteger} compare as
     * {@code BigDecimal}, and only pairs with a {@code float}/{@code double} side use IEEE
     * {@code double} semantics (so {@code NaN} never equals anything).
     */
    public static boolean compareNumbers(Number left, Number right, Clauses clause) {
        if (isIntegral(left) && isIntegral(right)) {
            return matchesOrdering(Long.compare(left.longValue(), right.longValue()), clause);
        }
        if (isFloating(left) || isFloating(right)) {
            return compareDoubles(left.doubleValue(), right.doubleValue(), clause);
        }
        return matchesOrdering(toBigDecimal(left).compareTo(toBigDecimal(right)), clause);
    }

    /**
     * Total ordering over numbers with the same precision rules as
     * {@link #compareNumbers(Number, Number, Clauses)}; {@code double} pairs use
     * {@link Double#compare(double, double)}.
     */
    public static int compareNumeric(Number left, Number right) {
        if (isIntegral(left) && isIntegral(right)) {
            return Long.compare(left.longValue(), right.longValue());
        }
        if (isFloating(left) || isFloating(right)) {
            return Double.compare(left.doubleValue(), right.doubleValue());
        }
        return toBigDecimal(left).compareTo(toBigDecimal(right));
    }

    private static boolean isIntegral(Number value) {
        return value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte
                || value instanceof AtomicInteger
                || value instanceof AtomicLong;
    }

    private static boolean isFloating(Number value) {
        return value instanceof Double || value instanceof Float;
    }

    private static BigDecimal toBigDecimal(Number value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (isIntegral(value)) {
            return BigDecimal.valueOf(value.longValue());
        }
        BigDecimal parsed = StringUtil.parseNumber(value.toString());
        return parsed != null ? parsed : BigDecimal.valueOf(value.doubleValue());
    }

    private static boolean compareDoubles(double left, double right, Clauses clause) {
        return switch (clause) {
            case BIGGER -> left > right;
            case BIGGER_EQUAL, NOT_SMALLER -> left >= right;
            case EQUAL, IN -> left == right;
            case NOT_BIGGER, SMALLER_EQUAL -> left <= right;
            case NOT_EQUAL -> left != right;
            case SMALLER -> left < right;
            default -> false;
        };
    }

    private static boolean matchesOrdering(int comparison, Clauses clause) {
        return switch (clause) {
            case BIGGER -> comparison > 0;
            case BIGGER_EQUAL, NOT_SMALLER -> comparison >= 0;
            case EQUAL, IN -> comparison == 0;
            case NOT_BIGGER, SMALLER_EQUAL -> comparison <= 0;
            case NOT_EQUAL -> comparison != 0;
            case SMALLER -> comparison < 0;
            default -> false;
        };
    }

    private static boolean isOrderingClause(Clauses clause) {
        return clause == Clauses.BIGGER
                || clause == Clauses.BIGGER_EQUAL
                || clause == Clauses.NOT_SMALLER
                || clause == Clauses.SMALLER
                || clause == Clauses.SMALLER_EQUAL
                || clause == Clauses.NOT_BIGGER;
    }

    private static Integer parseIntegerCompatible(String value) {
        final int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c == '.' || c == 'e' || c == 'E') {
                return (int) Double.parseDouble(value);
            }
        }
        return Integer.valueOf(value);
    }

    private static Long parseLongCompatible(String value) {
        final int len = value.length();
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c == '.' || c == 'e' || c == 'E') {
                return (long) Double.parseDouble(value);
            }
        }
        return Long.valueOf(value);
    }

    private static boolean isNegatedSetClause(Clauses clause) {
        return clause == Clauses.NOT_BIGGER
                || clause == Clauses.NOT_EQUAL
                || clause == Clauses.NOT_SMALLER
                || clause == Clauses.NOT_CONTAINS
                || clause == Clauses.NOT_MATCHES;
    }

    private static boolean isTextMatchClause(Clauses clause) {
        return clause == Clauses.CONTAINS
                || clause == Clauses.MATCHES
                || clause == Clauses.NOT_CONTAINS
                || clause == Clauses.NOT_MATCHES;
    }

    private static boolean evaluateIterableComparison(Object fieldValue,
                                                      Iterable<?> compareValues,
                                                      Clauses clause,
                                                      String dateFormat,
                                                      boolean negatedSetClause) {
        if (negatedSetClause) {
            for (Object compareValue : compareValues) {
                if (!compare(fieldValue, compareValue, clause, dateFormat)) {
                    return false;
                }
            }
            return true;
        }

        for (Object compareValue : compareValues) {
            if (compare(fieldValue, compareValue, clause, dateFormat)) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateArrayComparison(Object fieldValue,
                                                   Object compareArray,
                                                   Clauses clause,
                                                   String dateFormat,
                                                   boolean negatedSetClause) {
        if (compareArray instanceof Object[] objects) {
            if (negatedSetClause) {
                for (Object compareValue : objects) {
                    if (!compare(fieldValue, compareValue, clause, dateFormat)) {
                        return false;
                    }
                }
                return true;
            }

            for (Object compareValue : objects) {
                if (compare(fieldValue, compareValue, clause, dateFormat)) {
                    return true;
                }
            }
            return false;
        }

        final int length = Array.getLength(compareArray);

        if (negatedSetClause) {
            for (int i = 0; i < length; i++) {
                if (!compare(fieldValue, Array.get(compareArray, i), clause, dateFormat)) {
                    return false;
                }
            }
            return true;
        }

        for (int i = 0; i < length; i++) {
            if (compare(fieldValue, Array.get(compareArray, i), clause, dateFormat)) {
                return true;
            }
        }
        return false;
    }

    private static boolean compareBooleanValues(boolean fieldValue,
                                                Object compareValue,
                                                Clauses clause) {
        final Boolean right;

        if (compareValue instanceof Boolean b) {
            right = b;
        } else {
            String s = (compareValue instanceof String str) ? str : castToString(compareValue);
            right = StringUtil.parseBoolStrict(s);
        }

        if (right == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("compareValue [{}] is not a convertible boolean", compareValue);
            }
            return false;
        }

        return switch (clause) {
            case EQUAL, IN -> fieldValue == right;
            case NOT_EQUAL -> fieldValue != right;
            default -> false;
        };
    }

    private static boolean compareStringValues(String fieldValue,
                                               Object compareValue,
                                               Clauses clause,
                                               String dateFormat) {
        if (compareValue instanceof Number number && isOrderingClause(clause)) {
            // A numeric bound against a text field means numeric intent ("10" > 9).
            BigDecimal parsed = StringUtil.parseNumber(fieldValue);
            return parsed != null && compareNumbers(parsed, number, clause);
        }
        final String right = comparableText(compareValue, dateFormat);

        return switch (clause) {
            case EQUAL, IN -> Objects.equals(fieldValue, right);
            case NOT_EQUAL -> !Objects.equals(fieldValue, right);
            case CONTAINS -> right != null && fieldValue.contains(right);
            case MATCHES -> right != null && regexPattern(right).matcher(fieldValue).matches();
            case NOT_CONTAINS -> right != null && !fieldValue.contains(right);
            case NOT_MATCHES -> right != null && !regexPattern(right).matcher(fieldValue).matches();
            case BIGGER, BIGGER_EQUAL, NOT_SMALLER, SMALLER, SMALLER_EQUAL, NOT_BIGGER ->
                    right != null && matchesOrdering(fieldValue.compareTo(right), clause);
        };
    }

    private static String comparableText(Object value, String dateFormat) {
        return switch (value) {
            case String s -> s;
            case Enum<?> e -> e.name();
            case Character c -> String.valueOf(c);
            default -> castToString(value, dateFormat);
        };
    }

    /**
     * Other value types (UUID, LocalTime, ...): a same-type {@code Comparable} compares
     * natively; anything else compares by its text form.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean compareOtherValues(Object fieldValue,
                                              Object compareValue,
                                              Clauses clause,
                                              String dateFormat) {
        if (fieldValue.getClass().isInstance(compareValue) && !isTextMatchClause(clause)) {
            if (fieldValue instanceof Comparable comparable) {
                return matchesOrdering(comparable.compareTo(compareValue), clause);
            }
            return switch (clause) {
                case EQUAL, IN -> fieldValue.equals(compareValue);
                case NOT_EQUAL -> !fieldValue.equals(compareValue);
                default -> false;
            };
        }
        return compareStringValues(String.valueOf(fieldValue), compareValue, clause, dateFormat);
    }

    private static boolean compareEnumValues(Enum<?> fieldValue,
                                             Object compareValue,
                                             Clauses clause,
                                             String dateFormat) {
        if (!isOrderingClause(clause)) {
            return compareStringValues(fieldValue.name(), compareValue, clause, dateFormat);
        }
        // Ordering follows declaration order, the same order enums sort in.
        Enum<?> bound = resolveEnumConstant(fieldValue.getDeclaringClass(), compareValue);
        return bound != null && matchesOrdering(Integer.compare(fieldValue.ordinal(), bound.ordinal()), clause);
    }

    private static Enum<?> resolveEnumConstant(Class<?> enumType, Object value) {
        if (enumType.isInstance(value)) {
            return (Enum<?>) value;
        }
        if (value instanceof String name) {
            for (Object constant : enumType.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name)) {
                    return (Enum<?>) constant;
                }
            }
        }
        return null;
    }

    /**
     * Date/time comparison. With an explicit {@code dateFormat}, both sides are normalized
     * to that format's precision (legacy fluent semantics). Without one:
     * <ul>
     *   <li>a temporal compare value compares exactly (local vs local on the local
     *   timeline, otherwise as instants in the system zone);</li>
     *   <li>a text literal compares at the precision it is written: {@code '2024-01-02'}
     *   covers that day, {@code '2024-01-02 10:00:00'} that second,
     *   {@code '2024-01-02T10:00:00.500Z'} that millisecond.</li>
     * </ul>
     */
    private static boolean compareDateValues(Object fieldValue,
                                             Object compareValue,
                                             Clauses clause,
                                             String dateFormat) {
        if (dateFormat == null || dateFormat.isEmpty()) {
            if (isDateLike(compareValue)) {
                return matchesOrdering(TemporalComparison.compareExact(fieldValue, compareValue), clause);
            }
            if (compareValue instanceof String literal) {
                TemporalComparison.Literal parsed = TemporalComparison.parseLiteral(literal);
                if (parsed != null) {
                    return matchesOrdering(parsed.compareField(fieldValue), clause);
                }
                if (LOG.isWarnEnabled()) {
                    LOG.warn("compareValue [{}] is not a date literal", compareValue);
                }
                return false;
            }
        }
        final Long left = normalizeToEpochMillis(fieldValue, dateFormat);
        final Long right = normalizeToEpochMillis(compareValue, dateFormat);

        if (left == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("fieldValue [{}] is not a convertible date", fieldValue);
            }
            return false;
        }

        if (right == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("compareValue [{}] is not a convertible date", compareValue);
            }
            return false;
        }

        return switch (clause) {
            case BIGGER -> left > right;
            case BIGGER_EQUAL, NOT_SMALLER -> left >= right;
            case EQUAL, IN -> left.longValue() == right.longValue();
            case NOT_BIGGER, SMALLER_EQUAL -> left <= right;
            case NOT_EQUAL -> left.longValue() != right.longValue();
            case SMALLER -> left < right;
            default -> false;
        };
    }

    /**
     * Preserves old semantics:
     * normalize according to the active date format before comparing.
     */
    private static Long normalizeToEpochMillis(Object value, String dateFormat) {
        if (value == null) {
            return null;
        }
        return datePlan(dateFormat).normalize(value);
    }

    public static boolean isDateLike(Object value) {
        return value instanceof Date
                || value instanceof Instant
                || value instanceof LocalDate
                || value instanceof LocalDateTime
                || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime;
    }

    private enum DatePlanType {
        YEAR,
        YEAR_MONTH,
        DATE,
        DATE_HOUR,
        DATE_MINUTE,
        DATE_SECOND,
        GENERIC
    }

    private static final class DateFormatPlan {
        private static final int PARSED_STRING_CACHE_MAX_ENTRIES = 256;

        private final String pattern;
        private final DateTimeFormatter formatter;
        private final DatePlanType type;
        private final BoundedCache<String, Long> parsedStrings = new BoundedCache<>(PARSED_STRING_CACHE_MAX_ENTRIES);

        private DateFormatPlan(String pattern, DateTimeFormatter formatter, DatePlanType type) {
            this.pattern = pattern;
            this.formatter = formatter;
            this.type = type;
        }

        static DateFormatPlan create(String pattern) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return new DateFormatPlan(pattern, formatter, detectType(pattern));
        }

        DateTimeFormatter formatter() {
            return formatter;
        }

        String format(Instant instant) {
            return formatter.format(instant.atZone(systemZone()));
        }

        Long normalize(Object value) {
            try {
                ZoneId systemZone = systemZone();
                return switch (value) {
                    case Date d -> normalizeInstant(d.toInstant(), systemZone);
                    case Instant instant -> normalizeInstant(instant, systemZone);
                    case ZonedDateTime zdt -> normalizeZoned(zdt.withZoneSameInstant(systemZone), systemZone);
                    case OffsetDateTime odt -> normalizeInstant(odt.toInstant(), systemZone);
                    case LocalDateTime ldt -> normalizeLocalDateTime(ldt, systemZone);
                    case LocalDate ld -> normalizeLocalDate(ld, systemZone);
                    case String s -> normalizeString(s, systemZone);
                    default -> normalizeString(String.valueOf(value), systemZone);
                };
            } catch (DateTimeException | IllegalArgumentException e) {
                return null;
            }
        }

        private Long normalizeInstant(Instant instant, ZoneId systemZone) {
            return normalizeZoned(instant.atZone(systemZone), systemZone);
        }

        private Long normalizeZoned(ZonedDateTime zdt, ZoneId systemZone) {
            return switch (type) {
                case YEAR -> Year.of(zdt.getYear())
                        .atDay(1)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case YEAR_MONTH -> YearMonth.of(zdt.getYear(), zdt.getMonthValue())
                        .atDay(1)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE -> zdt.toLocalDate()
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                // Truncate the zoned value itself so its offset survives: rebuilding a
                // LocalDateTime would fold the two instants of a DST overlap together.
                case DATE_HOUR -> zdt.truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli();
                case DATE_MINUTE -> zdt.truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli();
                case DATE_SECOND -> zdt.truncatedTo(ChronoUnit.SECONDS).toInstant().toEpochMilli();
                case GENERIC -> normalizeGeneric(zdt, systemZone);
            };
        }

        private Long normalizeLocalDateTime(LocalDateTime ldt, ZoneId systemZone) {
            return switch (type) {
                case YEAR -> Year.of(ldt.getYear())
                        .atDay(1)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case YEAR_MONTH -> YearMonth.of(ldt.getYear(), ldt.getMonthValue())
                        .atDay(1)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE -> ldt.toLocalDate()
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE_HOUR -> LocalDateTime.of(
                                ldt.getYear(),
                                ldt.getMonthValue(),
                                ldt.getDayOfMonth(),
                                ldt.getHour(),
                                0,
                                0,
                                0)
                        .atZone(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE_MINUTE -> LocalDateTime.of(
                                ldt.getYear(),
                                ldt.getMonthValue(),
                                ldt.getDayOfMonth(),
                                ldt.getHour(),
                                ldt.getMinute(),
                                0,
                                0)
                        .atZone(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE_SECOND -> LocalDateTime.of(
                                ldt.getYear(),
                                ldt.getMonthValue(),
                                ldt.getDayOfMonth(),
                                ldt.getHour(),
                                ldt.getMinute(),
                                ldt.getSecond(),
                                0)
                        .atZone(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case GENERIC -> normalizeGeneric(ldt.atZone(systemZone), systemZone);
            };
        }

        private Long normalizeLocalDate(LocalDate ld, ZoneId systemZone) {
            return switch (type) {
                case YEAR -> Year.of(ld.getYear())
                        .atDay(1)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case YEAR_MONTH -> YearMonth.of(ld.getYear(), ld.getMonthValue())
                        .atDay(1)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE, DATE_HOUR, DATE_MINUTE, DATE_SECOND -> ld
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case GENERIC -> normalizeGeneric(ld.atStartOfDay(systemZone), systemZone);
            };
        }

        private Long normalizeString(String value, ZoneId systemZone) {
            // Literal compare values are re-normalized for every row; memoize per zone.
            return parsedStrings.getOrCompute(systemZone.getId() + '|' + value,
                    ignored -> parseString(value, systemZone));
        }

        private Long parseString(String value, ZoneId systemZone) {
            try {
                return normalizeFormattedString(value, systemZone);
            } catch (DateTimeException formatFailure) {
                Long iso = normalizeIsoString(value.trim(), systemZone);
                if (iso == null) {
                    throw formatFailure;
                }
                return iso;
            }
        }

        /**
         * Accepts ISO-8601 literals ({@code 2024-01-02}, {@code 2024-01-02T10:15:30},
         * {@code 2024-01-02T10:15:30Z}, offsets, zone ids) when the configured format
         * does not match, then normalizes them to the configured precision.
         */
        private Long normalizeIsoString(String value, ZoneId systemZone) {
            try {
                if (value.indexOf('T') < 0) {
                    return normalizeLocalDate(LocalDate.parse(value), systemZone);
                }
                TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(
                        value,
                        ZonedDateTime::from,
                        LocalDateTime::from
                );
                return switch (parsed) {
                    case ZonedDateTime zdt -> normalizeZoned(zdt.withZoneSameInstant(systemZone), systemZone);
                    case LocalDateTime ldt -> normalizeLocalDateTime(ldt, systemZone);
                    default -> null;
                };
            } catch (DateTimeException ignored) {
                return null;
            }
        }

        private Long normalizeFormattedString(String value, ZoneId systemZone) {
            return switch (type) {
                case YEAR -> Year.parse(value, formatter)
                        .atDay(1)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case YEAR_MONTH -> YearMonth.parse(value, formatter)
                        .atDay(1)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE -> LocalDate.parse(value, formatter)
                        .atStartOfDay(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE_HOUR, DATE_MINUTE, DATE_SECOND, GENERIC -> parseGenericString(value, systemZone);
            };
        }

        private Long parseGenericString(String value, ZoneId systemZone) {
            TemporalAccessor parsed = formatter.parseBest(
                    value,
                    ZonedDateTime::from,
                    OffsetDateTime::from,
                    LocalDateTime::from,
                    LocalDate::from
            );

            return switch (parsed) {
                case ZonedDateTime zdt -> normalizeZoned(zdt.withZoneSameInstant(systemZone), systemZone);
                case OffsetDateTime odt -> normalizeInstant(odt.toInstant(), systemZone);
                case LocalDateTime ldt -> normalizeLocalDateTime(ldt, systemZone);
                case LocalDate ld -> normalizeLocalDate(ld, systemZone);
                default -> Instant.from(parsed).toEpochMilli();
            };
        }

        /**
         * Exact semantic fallback:
         * format the value using the formatter, then parse it back.
         */
        private Long normalizeGeneric(ZonedDateTime value, ZoneId systemZone) {
            String formatted = formatter.format(value);
            TemporalAccessor parsed = formatter.parseBest(
                    formatted,
                    ZonedDateTime::from,
                    OffsetDateTime::from,
                    LocalDateTime::from,
                    LocalDate::from
            );

            return switch (parsed) {
                case ZonedDateTime zdt -> zdt.toInstant().toEpochMilli();
                case OffsetDateTime odt -> odt.toInstant().toEpochMilli();
                case LocalDateTime ldt -> ldt.atZone(systemZone).toInstant().toEpochMilli();
                case LocalDate ld -> ld.atStartOfDay(systemZone).toInstant().toEpochMilli();
                default -> Instant.from(parsed).toEpochMilli();
            };
        }

        private static DatePlanType detectType(String pattern) {
            return switch (pattern) {
                case "yyyy" -> DatePlanType.YEAR;
                case "yyyy-MM" -> DatePlanType.YEAR_MONTH;
                case "yyyy-MM-dd" -> DatePlanType.DATE;
                case "yyyy-MM-dd HH" -> DatePlanType.DATE_HOUR;
                case "yyyy-MM-dd HH:mm" -> DatePlanType.DATE_MINUTE;
                case "yyyy-MM-dd HH:mm:ss" -> DatePlanType.DATE_SECOND;
                default -> DatePlanType.GENERIC;
            };
        }

        @Override
        public String toString() {
            return "DateFormatPlan[" + pattern + "," + type + ']';
        }
    }

    private static final class BoundedCache<K, V> {
        private static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private final LinkedHashMap<K, V> delegate;

        private BoundedCache(int maxEntries) {
            this.delegate = new LinkedHashMap<>(maxEntries, DEFAULT_LOAD_FACTOR, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return BoundedCache.this.size() > maxEntries;
                }
            };
        }

        private V getOrCompute(K key, Function<? super K, ? extends V> factory) {
            synchronized (delegate) {
                V cached = delegate.get(key);
                if (cached != null) {
                    return cached;
                }
                V created = factory.apply(key);
                delegate.put(key, created);
                return created;
            }
        }

        private void clear() {
            synchronized (delegate) {
                delegate.clear();
            }
        }

        private int size() {
            synchronized (delegate) {
                return delegate.size();
            }
        }
    }
}
