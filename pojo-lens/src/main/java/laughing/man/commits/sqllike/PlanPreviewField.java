package laughing.man.commits.sqllike;

import java.util.List;
import java.util.Objects;

/**
 * Structural description of a single SELECT field in a {@link SqlLikePlanPreview}.
 */
public final class PlanPreviewField {

    private final String field;
    private final String outputName;
    private final String alias;
    private final String metric;
    private final String timeBucket;
    private final String windowFunction;
    private final List<String> windowPartitionFields;
    private final List<String> windowOrderFields;
    private final String windowFrame;
    private final boolean computed;
    private final boolean countAll;

    public PlanPreviewField(String field,
                            String outputName,
                            String alias,
                            String metric,
                            String timeBucket,
                            String windowFunction,
                            List<String> windowPartitionFields,
                            List<String> windowOrderFields,
                            String windowFrame,
                            boolean computed,
                            boolean countAll) {
        this.field = Objects.requireNonNull(field, "field must not be null");
        this.outputName = Objects.requireNonNull(outputName, "outputName must not be null");
        this.alias = alias;
        this.metric = metric;
        this.timeBucket = timeBucket;
        this.windowFunction = windowFunction;
        this.windowPartitionFields = List.copyOf(
                Objects.requireNonNull(windowPartitionFields, "windowPartitionFields must not be null"));
        this.windowOrderFields = List.copyOf(
                Objects.requireNonNull(windowOrderFields, "windowOrderFields must not be null"));
        this.windowFrame = windowFrame;
        this.computed = computed;
        this.countAll = countAll;
    }

    /**
     * Returns the source field name or expression text.
     *
     * @return field name
     */
    public String field() {
        return field;
    }

    /**
     * Returns the output name for this field (alias if set, otherwise derived).
     *
     * @return output name
     */
    public String outputName() {
        return outputName;
    }

    /**
     * Returns the explicit alias, or {@code null} when none was declared.
     *
     * @return alias or null
     */
    public String alias() {
        return alias;
    }

    /**
     * Returns the aggregate function name (e.g. {@code "COUNT"}, {@code "SUM"}),
     * or {@code null} for non-metric fields.
     *
     * @return metric function name or null
     */
    public String metric() {
        return metric;
    }

    /**
     * Returns the time bucket preset name (e.g. {@code "DAY"}, {@code "MONTH"}),
     * or {@code null} for non-time-bucket fields.
     *
     * @return time bucket name or null
     */
    public String timeBucket() {
        return timeBucket;
    }

    /**
     * Returns the window function name (e.g. {@code "ROW_NUMBER"}, {@code "RANK"}),
     * or {@code null} for non-window fields.
     *
     * @return window function name or null
     */
    public String windowFunction() {
        return windowFunction;
    }

    /**
     * Returns the PARTITION BY field names for window fields.
     * Empty when the field is not a window field.
     *
     * @return partition fields
     */
    public List<String> windowPartitionFields() {
        return windowPartitionFields;
    }

    /**
     * Returns the ORDER BY field names within the window frame.
     * Empty when the field is not a window field or has no window ordering.
     *
     * @return window order fields
     */
    public List<String> windowOrderFields() {
        return windowOrderFields;
    }

    /**
     * Returns the SQL ROWS frame expression for the window field
     * (e.g. {@code "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"}),
     * or {@code null} for non-window fields.
     *
     * @return window frame expression or null
     */
    public String windowFrame() {
        return windowFrame;
    }

    /**
     * Returns true when the field is a computed arithmetic expression.
     *
     * @return true for computed fields
     */
    public boolean isComputed() {
        return computed;
    }

    /**
     * Returns true when the field represents {@code COUNT(*)}.
     *
     * @return true for COUNT(*) fields
     */
    public boolean isCountAll() {
        return countAll;
    }

    /**
     * Returns true when the field uses a window function.
     *
     * @return true for window fields
     */
    public boolean isWindow() {
        return windowFunction != null;
    }

    /**
     * Returns true when the field uses a metric aggregate function.
     *
     * @return true for metric fields
     */
    public boolean isMetric() {
        return metric != null;
    }

    /**
     * Returns true when the field uses a time bucket.
     *
     * @return true for time bucket fields
     */
    public boolean isTimeBucket() {
        return timeBucket != null;
    }
}
