package laughing.man.commits.util;

import laughing.man.commits.EngineDefaults;
import laughing.man.commits.enums.Clauses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
        if (isScalarComparable(compareObject)) {
            return compare(fieldValue, compareObject, clause, dateFormat);
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Could not cast/compare field as requested for filter rule [{}]",
                    compareObject.getClass().getSimpleName());
        }
        return false;
    }

    public static String castToString(Object fieldValue) {
        return castToString(fieldValue, null);
    }

    public static String castToString(Object fieldValue, String dateFormat) {
        try {
            if (fieldValue instanceof Date date) {
                return datePlan(dateFormat).format(date.toInstant());
            }
            if (fieldValue instanceof Instant instant) {
                return datePlan(dateFormat).format(instant);
            }
            if (fieldValue instanceof ZonedDateTime zdt) {
                return datePlan(dateFormat).format(zdt.toInstant());
            }
            if (fieldValue instanceof OffsetDateTime odt) {
                return datePlan(dateFormat).format(odt.toInstant());
            }
            if (fieldValue instanceof LocalDateTime ldt) {
                return datePlan(dateFormat).formatter().format(ldt);
            }
            if (fieldValue instanceof LocalDate ld) {
                return datePlan(dateFormat).formatter().format(ld);
            }
            return String.valueOf(fieldValue);
        } catch (Exception e) {
            LOG.error("Failed to cast field [{}]", fieldValue, e);
            return null;
        }
    }

    public static <T> T value(Object value, Class<T> cls) throws Exception {
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
            LOG.error("Cannot cast value [{}] to type [{}]",
                    fieldValue,
                    cls == null ? "null" : cls.getSimpleName(),
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
            if (isDateLike(fieldValue)) {
                return compareDateValues(fieldValue, compareValue, clause, dateFormat);
            }
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
        final double left = fieldValue.doubleValue();

        if (compareValue instanceof Number n) {
            return compareNumbers(left, n.doubleValue(), clause);
        }

        final String s = (compareValue instanceof String str) ? str : castToString(compareValue);
        if (s != null && StringUtil.isNumber(s)) {
            return compareNumbers(left, Double.parseDouble(s), clause);
        }

        if (LOG.isWarnEnabled() && compareValue != null) {
            LOG.warn("compareValue [{}] type [{}] is not a convertible numeric type",
                    compareValue, compareValue.getClass().getSimpleName());
        }
        return false;
    }

    private static boolean compareNumbers(double left, double right, Clauses clause) {
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
                || clause == Clauses.NOT_SMALLER;
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
        final String right = (compareValue instanceof String s)
                ? s
                : castToString(compareValue, dateFormat);

        return switch (clause) {
            case EQUAL, IN -> Objects.equals(fieldValue, right);
            case NOT_EQUAL -> !Objects.equals(fieldValue, right);
            case CONTAINS -> right != null && fieldValue.contains(right);
            case MATCHES -> right != null && regexPattern(right).matcher(fieldValue).matches();
            default -> false;
        };
    }

    private static boolean compareDateValues(Object fieldValue,
                                             Object compareValue,
                                             Clauses clause,
                                             String dateFormat) {
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
            case BIGGER_EQUAL -> left >= right;
            case EQUAL, IN -> left.longValue() == right.longValue();
            case NOT_BIGGER -> left <= right;
            case NOT_EQUAL -> left.longValue() != right.longValue();
            case NOT_SMALLER -> left >= right;
            case SMALLER -> left < right;
            case SMALLER_EQUAL -> left <= right;
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

    private static boolean isScalarComparable(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof String
                || isDateLike(value);
    }

    private static boolean isDateLike(Object value) {
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
        private final String pattern;
        private final DateTimeFormatter formatter;
        private final DatePlanType type;

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
                if (value instanceof Date d) {
                    return normalizeInstant(d.toInstant(), systemZone);
                }
                if (value instanceof Instant instant) {
                    return normalizeInstant(instant, systemZone);
                }
                if (value instanceof ZonedDateTime zdt) {
                    return normalizeZoned(zdt.withZoneSameInstant(systemZone), systemZone);
                }
                if (value instanceof OffsetDateTime odt) {
                    return normalizeInstant(odt.toInstant(), systemZone);
                }
                if (value instanceof LocalDateTime ldt) {
                    return normalizeLocalDateTime(ldt, systemZone);
                }
                if (value instanceof LocalDate ld) {
                    return normalizeLocalDate(ld, systemZone);
                }
                if (value instanceof String s) {
                    return normalizeString(s, systemZone);
                }

                String s = String.valueOf(value);
                return normalizeString(s, systemZone);
            } catch (Exception e) {
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
                case DATE_HOUR -> LocalDateTime.of(
                                zdt.getYear(),
                                zdt.getMonthValue(),
                                zdt.getDayOfMonth(),
                                zdt.getHour(),
                                0,
                                0,
                                0)
                        .atZone(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE_MINUTE -> LocalDateTime.of(
                                zdt.getYear(),
                                zdt.getMonthValue(),
                                zdt.getDayOfMonth(),
                                zdt.getHour(),
                                zdt.getMinute(),
                                0,
                                0)
                        .atZone(systemZone)
                        .toInstant()
                        .toEpochMilli();
                case DATE_SECOND -> LocalDateTime.of(
                                zdt.getYear(),
                                zdt.getMonthValue(),
                                zdt.getDayOfMonth(),
                                zdt.getHour(),
                                zdt.getMinute(),
                                zdt.getSecond(),
                                0)
                        .atZone(systemZone)
                        .toInstant()
                        .toEpochMilli();
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

            if (parsed instanceof ZonedDateTime zdt) {
                return normalizeZoned(zdt.withZoneSameInstant(systemZone), systemZone);
            }
            if (parsed instanceof OffsetDateTime odt) {
                return normalizeInstant(odt.toInstant(), systemZone);
            }
            if (parsed instanceof LocalDateTime ldt) {
                return normalizeLocalDateTime(ldt, systemZone);
            }
            if (parsed instanceof LocalDate ld) {
                return normalizeLocalDate(ld, systemZone);
            }
            return Instant.from(parsed).toEpochMilli();
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

            if (parsed instanceof ZonedDateTime zdt) {
                return zdt.toInstant().toEpochMilli();
            }
            if (parsed instanceof OffsetDateTime odt) {
                return odt.toInstant().toEpochMilli();
            }
            if (parsed instanceof LocalDateTime ldt) {
                return ldt.atZone(systemZone).toInstant().toEpochMilli();
            }
            if (parsed instanceof LocalDate ld) {
                return ld.atStartOfDay(systemZone).toInstant().toEpochMilli();
            }
            return Instant.from(parsed).toEpochMilli();
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
        private final LinkedHashMap<K, V> delegate;

        private BoundedCache(int maxEntries) {
            this.delegate = new LinkedHashMap<>(maxEntries, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > maxEntries;
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
