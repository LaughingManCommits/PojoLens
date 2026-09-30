package laughing.man.commits.sqllike.internal.expression;

import laughing.man.commits.enums.TimeBucket;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.TextLiteral;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.ZoneLiteral;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator.ValueResolver;
import laughing.man.commits.time.TimeBucketPreset;
import laughing.man.commits.util.TimeBucketUtil;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Date-part functions (WP-29): {@code name(value[, 'Zone/Id'])} returns an {@code Integer}.
 * Values are read through {@link TimeBucketUtil#localDateTime}, the normalization time
 * buckets use, with the same default zone (UTC) and zone parsing. So {@code year(x, zone)}
 * always equals the year of {@code bucket(x, 'year', zone)}, and likewise for the other
 * parts. A {@code LocalDate} has hour and minute {@code 0}.
 */
enum DatePartFunction implements ExpressionFunction {

    YEAR(LocalDateTime::getYear),
    QUARTER(local -> local.get(IsoFields.QUARTER_OF_YEAR)),
    MONTH(LocalDateTime::getMonthValue),
    DAY(LocalDateTime::getDayOfMonth),
    HOUR(LocalDateTime::getHour),
    MINUTE(LocalDateTime::getMinute),
    /**
     * ISO day of week: 1 is Monday, 7 is Sunday (PostgreSQL {@code isodow}).
     */
    DAY_OF_WEEK(local -> local.getDayOfWeek().getValue());

    private static final ZoneId DEFAULT_ZONE = TimeBucketPreset.of(TimeBucket.DAY).zoneId();
    private static final String SUPPORTED_TYPES =
            "Date, Instant, LocalDate, LocalDateTime, OffsetDateTime, or ZonedDateTime";
    private static final Map<String, DatePartFunction> BY_NAME = indexByName();

    private final ToIntFunction<LocalDateTime> part;

    DatePartFunction(ToIntFunction<LocalDateTime> part) {
        this.part = part;
    }

    /**
     * @return the date part for an upper-case name, or {@code null}
     */
    static DatePartFunction lookup(String upperName) {
        return BY_NAME.get(upperName);
    }

    @Override
    public void requireArgumentCount(String calledName, int count) {
        if (count < 1 || count > 2) {
            throw new IllegalArgumentException(
                    "Function " + calledName.toUpperCase(Locale.ROOT) + " requires 1 to 2 argument(s)");
        }
    }

    /**
     * Parses the optional zone once; it must be a text literal.
     */
    @Override
    public List<ExpressionNode> prepare(List<ExpressionNode> args) {
        if (args.size() < 2) {
            return args;
        }
        if (!(args.get(1) instanceof TextLiteral zone)) {
            throw new IllegalArgumentException("Function " + name()
                    + " zone must be a text literal such as 'Europe/Amsterdam'");
        }
        ZoneId zoneId = TimeBucketPreset.of(TimeBucket.DAY).withZone(zone.literal()).zoneId();
        return List.of(args.get(0), new ZoneLiteral(zoneId));
    }

    @Override
    public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
        Object value = args.get(0).value(row, slots, resolver);
        if (value == null) {
            return null;
        }
        if (!TimeBucketUtil.supportsTimeBucketType(value.getClass())) {
            throw new IllegalArgumentException("Function " + name() + " needs a date/time value, not "
                    + value.getClass().getSimpleName());
        }
        ZoneId zone = args.size() == 2 ? ((ZoneLiteral) args.get(1)).zone() : DEFAULT_ZONE;
        return part.applyAsInt(TimeBucketUtil.localDateTime(value, zone));
    }

    @Override
    public double number(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
        Object value = value(args, row, slots, resolver);
        return value == null ? Double.NaN : ((Integer) value).doubleValue();
    }

    @Override
    public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
        Class<?> type = args.get(0).type(fieldTypes);
        if (ExpressionTypes.kindOf(type) != ExpressionTypes.Kind.ANY && !TimeBucketUtil.supportsTimeBucketType(type)) {
            throw new IllegalArgumentException("Function " + name() + " needs a date/time value ("
                    + SUPPORTED_TYPES + "), not " + ExpressionTypes.describe(type));
        }
        return Integer.class;
    }

    private static Map<String, DatePartFunction> indexByName() {
        Map<String, DatePartFunction> byName = new HashMap<>();
        for (DatePartFunction function : values()) {
            byName.put(function.name(), function);
        }
        return Map.copyOf(byName);
    }
}
