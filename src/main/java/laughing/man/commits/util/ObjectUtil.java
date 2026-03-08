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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Value casting and comparison helpers used by the filtering engine.
 */
public class ObjectUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectUtil.class);
    private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

    /**
     * Compares a field value against one or more compare values.
     */
    public static boolean compareObject(Object fieldValue, Object compareObject,
            Clauses clause, String dateFormat) {
        if (compareObject == null) {
            if (Clauses.EQUAL.equals(clause)) {
                return fieldValue == null;
            }
            if (Clauses.NOT_EQUAL.equals(clause)) {
                return fieldValue != null;
            }
            return false;
        }
        boolean negatedSetClause = isNegatedSetClause(clause);
        if (compareObject instanceof Map<?, ?> compareMap) {
            return evaluateCollectionComparison(fieldValue, compareMap.values(), clause, dateFormat, negatedSetClause);
        } else if (compareObject instanceof Collection<?> compareValues) {
            return evaluateCollectionComparison(fieldValue, compareValues, clause, dateFormat, negatedSetClause);
        } else if (compareObject instanceof Iterable<?> compareValues) {
            return evaluateIterableComparison(fieldValue, compareValues, clause, dateFormat, negatedSetClause);
        } else if (compareObject.getClass().isArray()) {
            return evaluateArrayComparison(fieldValue, compareObject, clause, dateFormat, negatedSetClause);
        } else if (isScalarComparable(compareObject)) {
            return compare(fieldValue, compareObject, clause, dateFormat);
        } else {
            LOG.info("Could not cast/compare field as requested for filter "
                    + "rule [" + compareObject.getClass().getSimpleName() + "]");
            return false;
        }
    }

    /**
     * Converts a value to string using default date formatting for dates.
     */
    public static String castToString(Object fieldValue) {
        return castToString(fieldValue, null);
    }

    /**
     * Converts a value to string using the provided date format when needed.
     */
    public static String castToString(Object fieldValue, String dateFormat) {
        try {
            if (fieldValue instanceof Date) {
                String effectiveDateFormat = dateFormat;
                if (StringUtil.isNull(effectiveDateFormat)) {
                    effectiveDateFormat = EngineDefaults.SDF;
                }
                DateTimeFormatter formatter = formatter(effectiveDateFormat);
                return formatDate((Date) fieldValue, formatter);
            } else {
                return String.valueOf(fieldValue);
            }

        } catch (Exception e) {
            LOG.error("Failed to cast field[" + fieldValue + "]", e);
        }
        return null;
    }

    /**
     * Compares single values after type normalization.
     */
    private static boolean compare(Object fieldValue, Object compareValue,
            Clauses clause, String dateFormat) {
        try {
            if (clause == null) {
                return false;
            }
            if (fieldValue instanceof Number) {
                if (compareValue instanceof Number) {
                    double fieldNumber = ((Number) fieldValue).doubleValue();
                    double compareNumber = ((Number) compareValue).doubleValue();
                    return compareNumbers(fieldNumber, compareNumber, clause);
                } else {
                    String value2 = castToString(compareValue);
                    if (StringUtil.isNumber(value2)) {
                        double fieldNumber = ((Number) fieldValue).doubleValue();
                        double compareNumber = Double.parseDouble(value2);
                        return compareNumbers(fieldNumber, compareNumber, clause);
                    }
                    LOG.warn("compareValue [" + compareValue + "] type "
                            + "[" + compareValue.getClass().getSimpleName() + "] "
                            + "is not a Convertible type Integer/Double/Float");
                }
            } else if (fieldValue instanceof Boolean) {
                return compareBooleanValues((Boolean) fieldValue, compareValue, clause);
            } else if (fieldValue instanceof String) {
                return compareStringValues(fieldValue, compareValue, clause, dateFormat);
            } else if (fieldValue instanceof Date) {
                return compareDateValues(fieldValue, compareValue, clause, dateFormat);
            }
        } catch (Exception e) {
            LOG.error("Failed to compare field[" + fieldValue + "] "
                    + "with field [" + compareValue + "] "
                    + "with clause [" + clause + "]", e);
        }
        return false;
    }

    /**
     * Generic helper for typed casts.
     */
    public static <T> T value(Object value, Class<T> cls) throws Exception {
        return cls.cast(value);
    }

    /**
     * Casts arbitrary input to the requested wrapper type when possible.
     */
    public static <T> T castValue(Object fieldValue, Class<T> cls) {
        try {
            if (fieldValue == null || cls == null) {
                return null;
            }
            if (cls.isInstance(fieldValue)) {
                return cls.cast(fieldValue);
            }
            String castValue = castToString(fieldValue, EngineDefaults.SDF);
            if (castValue == null) {
                return null;
            }
            if (cls.equals(Integer.class)) {
                return value(parseIntegerCompatible(castValue), cls);
            } else if (cls.equals(Double.class)) {
                return value(Double.valueOf(castValue), cls);
            } else if (cls.equals(Float.class)) {
                return value(Float.valueOf(castValue), cls);
            } else if (cls.equals(Long.class)) {
                return value(parseLongCompatible(castValue), cls);
            } else if (cls.equals(Boolean.class)) {
                Boolean parsed = StringUtil.parseBoolStrict(castValue);
                return value(parsed != null && parsed, cls);
            } else if (cls.equals(Date.class)) {
                Date parsed = parseDateStrict(castValue, formatter(EngineDefaults.SDF));
                return value(parsed, cls);
            }
            return value(castValue, cls);
        } catch (Exception e) {
            String typeName = cls == null ? "null" : cls.getSimpleName();
            LOG.error("Cannot Cast to value[" + fieldValue + "] "
                    + "to Type [" + typeName + "] ", e);
        }
        return null;
    }

    private ObjectUtil() {
    }

    private static DateTimeFormatter formatter(String dateFormat) {
        return FORMATTER_CACHE.computeIfAbsent(dateFormat, DateTimeFormatter::ofPattern);
    }

    private static Date normalizeDate(Object compareValue, DateTimeFormatter formatter) {
        if (compareValue instanceof Date) {
            return tryParseDate(formatDate((Date) compareValue, formatter), formatter);
        }
        String value = castToString(compareValue);
        if (value == null) {
            return null;
        }
        return tryParseDate(value, formatter);
    }

    private static boolean compareNumbers(double left, double right, Clauses clause) {
        return switch (clause) {
            case BIGGER -> left > right;
            case BIGGER_EQUAL -> left >= right;
            case EQUAL -> left == right;
            case IN -> left == right;
            case NOT_BIGGER -> !(left > right);
            case NOT_EQUAL -> left != right;
            case NOT_SMALLER -> !(left < right);
            case SMALLER -> left < right;
            case SMALLER_EQUAL -> left <= right;
            default -> false;
        };
    }

    private static Integer parseIntegerCompatible(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignore) {
            return Double.valueOf(value).intValue();
        }
    }

    private static Long parseLongCompatible(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignore) {
            return Double.valueOf(value).longValue();
        }
    }

    private static boolean isNegatedSetClause(Clauses clause) {
        return Clauses.NOT_BIGGER.equals(clause)
                || Clauses.NOT_EQUAL.equals(clause)
                || Clauses.NOT_SMALLER.equals(clause);
    }

    private static boolean evaluateCollectionComparison(Object fieldValue,
                                                        Collection<?> compareValues,
                                                        Clauses clause,
                                                        String dateFormat,
                                                        boolean negatedSetClause) {
        return evaluateIterableComparison(fieldValue, compareValues, clause, dateFormat, negatedSetClause);
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
        int length = Array.getLength(compareArray);
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
        String value = castToString(compareValue);
        Boolean compareBool = StringUtil.parseBoolStrict(value);
        if (compareBool == null) {
            LOG.warn("compareValue [" + value + "] is not a Convertible type Boolean");
            return false;
        }
        if (Clauses.EQUAL.equals(clause)) {
            return fieldValue == compareBool;
        }
        if (Clauses.IN.equals(clause)) {
            return fieldValue == compareBool;
        }
        if (Clauses.NOT_EQUAL.equals(clause)) {
            return fieldValue != compareBool;
        }
        return false;
    }

    private static boolean compareStringValues(Object fieldValue,
                                               Object compareValue,
                                               Clauses clause,
                                               String dateFormat) {
        String value1 = castToString(fieldValue, dateFormat);
        String value2 = castToString(compareValue, dateFormat);
        if (value1 == null) {
            LOG.warn("fieldValue [" + fieldValue + "] type "
                    + "[" + fieldValue.getClass().getSimpleName() + "] "
                    + "is not a Convertible type String");
            if (value2 == null && compareValue != null) {
                LOG.warn("compareValue [" + compareValue + "] type "
                        + "[" + compareValue.getClass().getSimpleName() + "] "
                        + "is not a Convertible type String");
            }
            return false;
        }
        return switch (clause) {
            case EQUAL -> value1.equals(value2);
            case IN -> value1.equals(value2);
            case NOT_EQUAL -> !value1.equals(value2);
            case CONTAINS -> value2 != null && value1.contains(value2);
            case MATCHES -> value2 != null && value1.matches(value2);
            default -> false;
        };
    }

    private static boolean compareDateValues(Object fieldValue,
                                             Object compareValue,
                                             Clauses clause,
                                             String dateFormat) {
        String format = StringUtil.isNull(dateFormat) ? EngineDefaults.SDF : dateFormat;
        DateTimeFormatter formatter = formatter(format);
        Date compareDate = normalizeDate(compareValue, formatter);
        if (compareDate == null) {
            LOG.warn("compareValue [" + compareValue + "] is not a Convertible type Date");
            return false;
        }
        Date fieldDate = tryParseDate(formatDate((Date) fieldValue, formatter), formatter);
        if (fieldDate == null) {
            LOG.warn("fieldValue [" + fieldValue + "] is not a Convertible type Date");
            return false;
        }
        return switch (clause) {
            case BIGGER -> fieldDate.after(compareDate);
            case BIGGER_EQUAL -> fieldDate.equals(compareDate) || fieldDate.after(compareDate);
            case EQUAL -> fieldDate.equals(compareDate);
            case IN -> fieldDate.equals(compareDate);
            case NOT_BIGGER -> !fieldDate.after(compareDate);
            case NOT_EQUAL -> !fieldDate.equals(compareDate);
            case NOT_SMALLER -> !fieldDate.before(compareDate);
            case SMALLER -> fieldDate.before(compareDate);
            case SMALLER_EQUAL -> fieldDate.before(compareDate) || fieldDate.equals(compareDate);
            default -> false;
        };
    }

    private static boolean isScalarComparable(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof String
                || value instanceof Date;
    }

    private static String formatDate(Date value, DateTimeFormatter formatter) {
        return formatter.format(Instant.ofEpochMilli(value.getTime()).atZone(ZoneId.systemDefault()));
    }

    private static Date tryParseDate(String value, DateTimeFormatter formatter) {
        try {
            return parseDateStrict(value, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Date parseDateStrict(String value, DateTimeFormatter formatter) {
        TemporalAccessor parsed = formatter.parseBest(
                value,
                ZonedDateTime::from,
                OffsetDateTime::from,
                LocalDateTime::from,
                LocalDate::from
        );
        if (parsed instanceof ZonedDateTime zonedDateTime) {
            return Date.from(zonedDateTime.toInstant());
        }
        if (parsed instanceof OffsetDateTime offsetDateTime) {
            return Date.from(offsetDateTime.toInstant());
        }
        if (parsed instanceof LocalDateTime localDateTime) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        if (parsed instanceof LocalDate localDate) {
            return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        return Date.from(Instant.from(parsed));
    }
}

