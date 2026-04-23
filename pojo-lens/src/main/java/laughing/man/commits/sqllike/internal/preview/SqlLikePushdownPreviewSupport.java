package laughing.man.commits.sqllike.internal.preview;

import laughing.man.commits.sqllike.PlanPreviewField;
import laughing.man.commits.sqllike.PlanPreviewFilter;
import laughing.man.commits.sqllike.SqlLikePlanPreview;
import laughing.man.commits.sqllike.SqlLikePushdownMode;
import laughing.man.commits.sqllike.SqlLikePushdownPreview;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal pushdown classification helpers.
 */
public final class SqlLikePushdownPreviewSupport {

    private static final Set<String> PUSHABLE_FILTER_OPERATORS = Set.of("=", "!=", "<", "<=", ">", ">=");

    private SqlLikePushdownPreviewSupport() {
    }

    public static SqlLikePushdownPreview buildFromPlan(SqlLikePlanPreview preview) {
        LinkedHashSet<String> pushableStages = new LinkedHashSet<>();
        LinkedHashSet<String> inMemoryStages = new LinkedHashSet<>();
        LinkedHashSet<String> fallbackReasons = new LinkedHashSet<>();

        classifySelect(preview, pushableStages, inMemoryStages, fallbackReasons);
        boolean wherePushable = classifyWhere(preview, pushableStages, inMemoryStages, fallbackReasons);
        classifyUnsupportedStages(preview, inMemoryStages, fallbackReasons);
        classifyOrderAndPaging(preview, wherePushable, pushableStages, inMemoryStages);

        SqlLikePushdownMode mode = resolveMode(pushableStages, fallbackReasons);
        return new SqlLikePushdownPreview(
                preview.source(),
                mode,
                List.copyOf(pushableStages),
                List.copyOf(inMemoryStages),
                List.copyOf(fallbackReasons)
        );
    }

    public static Map<String, Object> explainEntry(SqlLikePushdownPreview preview) {
        LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
        entry.put("mode", preview.mode().name());
        entry.put("pushableStages", preview.pushableStages());
        entry.put("inMemoryStages", preview.inMemoryStages());
        entry.put("fallbackReasons", preview.fallbackReasons());
        entry.put("fullyPushable", preview.isFullyPushable());
        return Collections.unmodifiableMap(entry);
    }

    private static void classifySelect(SqlLikePlanPreview preview,
                                       Set<String> pushableStages,
                                       Set<String> inMemoryStages,
                                       Set<String> fallbackReasons) {
        if (preview.isWildcard()) {
            return;
        }
        for (PlanPreviewField field : preview.selectFields()) {
            if (field.isComputed()) {
                inMemoryStages.add("SELECT");
                fallbackReasons.add("COMPUTED_SELECT_UNSUPPORTED");
            }
            if (field.isTimeBucket()) {
                inMemoryStages.add("SELECT");
                fallbackReasons.add("TIME_BUCKET_UNSUPPORTED");
            }
            if (field.isMetric()) {
                inMemoryStages.add("AGGREGATE");
                fallbackReasons.add("AGGREGATION_UNSUPPORTED");
            }
            if (field.isWindow()) {
                inMemoryStages.add("WINDOW");
                fallbackReasons.add("WINDOW_UNSUPPORTED");
            }
        }
        if (!inMemoryStages.contains("SELECT")
                && !preview.hasGrouping()
                && !preview.hasAggregation()
                && !preview.hasWindows()) {
            pushableStages.add("SELECT");
        }
    }

    private static boolean classifyWhere(SqlLikePlanPreview preview,
                                         Set<String> pushableStages,
                                         Set<String> inMemoryStages,
                                         Set<String> fallbackReasons) {
        if (preview.filters().isEmpty()) {
            return true;
        }
        boolean pushable = true;
        for (PlanPreviewFilter filter : preview.filters()) {
            if (!PUSHABLE_FILTER_OPERATORS.contains(filter.operator())) {
                fallbackReasons.add("FILTER_OPERATOR_UNSUPPORTED");
                pushable = false;
            }
            if (!"LITERAL".equals(filter.valueKind()) && !"PARAMETER".equals(filter.valueKind())) {
                fallbackReasons.add("FILTER_VALUE_UNSUPPORTED");
                pushable = false;
            }
        }
        if (pushable && !preview.hasSubqueries()) {
            pushableStages.add("WHERE");
            return true;
        }
        inMemoryStages.add("WHERE");
        return false;
    }

    private static void classifyUnsupportedStages(SqlLikePlanPreview preview,
                                                  Set<String> inMemoryStages,
                                                  Set<String> fallbackReasons) {
        if (preview.hasJoins()) {
            inMemoryStages.add("JOIN");
            fallbackReasons.add("JOIN_UNSUPPORTED");
        }
        if (preview.hasGrouping()) {
            inMemoryStages.add("GROUP_BY");
            fallbackReasons.add("GROUPING_UNSUPPORTED");
        }
        if (preview.hasAggregation()) {
            inMemoryStages.add("AGGREGATE");
            fallbackReasons.add("AGGREGATION_UNSUPPORTED");
        }
        if (preview.hasWindows()) {
            inMemoryStages.add("WINDOW");
            fallbackReasons.add("WINDOW_UNSUPPORTED");
        }
        if (!preview.havingFilters().isEmpty()) {
            inMemoryStages.add("HAVING");
            fallbackReasons.add("HAVING_UNSUPPORTED");
        }
        if (!preview.qualifyFilters().isEmpty()) {
            inMemoryStages.add("QUALIFY");
            fallbackReasons.add("QUALIFY_UNSUPPORTED");
        }
        if (preview.hasSubqueries()) {
            inMemoryStages.add("WHERE");
            fallbackReasons.add("SUBQUERY_UNSUPPORTED");
        }
    }

    private static void classifyOrderAndPaging(SqlLikePlanPreview preview,
                                               boolean wherePushable,
                                               Set<String> pushableStages,
                                               Set<String> inMemoryStages) {
        boolean canPushPostFilterStages = wherePushable
                && !preview.hasJoins()
                && !preview.hasGrouping()
                && !preview.hasAggregation()
                && !preview.hasWindows()
                && !preview.hasSubqueries()
                && preview.havingFilters().isEmpty()
                && preview.qualifyFilters().isEmpty();

        if (!preview.orderFields().isEmpty()) {
            if (canPushPostFilterStages) {
                pushableStages.add("ORDER_BY");
            } else {
                inMemoryStages.add("ORDER_BY");
            }
        }
        if (preview.hasPaging()) {
            if (canPushPostFilterStages) {
                if (preview.paging().hasLimit()) {
                    pushableStages.add("LIMIT");
                }
                if (preview.paging().hasOffset()) {
                    pushableStages.add("OFFSET");
                }
            } else {
                inMemoryStages.add("PAGING");
            }
        }
    }

    private static SqlLikePushdownMode resolveMode(Set<String> pushableStages,
                                                   Set<String> fallbackReasons) {
        if (fallbackReasons.isEmpty()) {
            return SqlLikePushdownMode.FULL;
        }
        if (hasRowReducingPushdown(pushableStages)) {
            return SqlLikePushdownMode.SPLIT;
        }
        return SqlLikePushdownMode.IN_MEMORY_ONLY;
    }

    private static boolean hasRowReducingPushdown(Set<String> pushableStages) {
        return pushableStages.contains("WHERE")
                || pushableStages.contains("ORDER_BY")
                || pushableStages.contains("LIMIT")
                || pushableStages.contains("OFFSET");
    }
}
