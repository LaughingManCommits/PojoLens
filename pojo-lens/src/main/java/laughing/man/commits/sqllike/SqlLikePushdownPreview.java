package laughing.man.commits.sqllike;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic pushdown-readiness preview for a SQL-like query.
 * <p>
 * The preview is advisory metadata for host-owned adapters. PojoLens does not
 * execute against a database or rewrite the query into vendor SQL; it tells a
 * host application which first-phase stages are safe to perform before handing
 * rows back to the in-memory engine.
 */
public final class SqlLikePushdownPreview {

    private final String source;
    private final SqlLikePushdownMode mode;
    private final List<String> pushableStages;
    private final List<String> inMemoryStages;
    private final List<String> fallbackReasons;

    public SqlLikePushdownPreview(String source,
                                  SqlLikePushdownMode mode,
                                  List<String> pushableStages,
                                  List<String> inMemoryStages,
                                  List<String> fallbackReasons) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.pushableStages = new java.util.ArrayList<>(Objects.requireNonNull(pushableStages,
                "pushableStages must not be null"));
        this.inMemoryStages = new java.util.ArrayList<>(Objects.requireNonNull(inMemoryStages,
                "inMemoryStages must not be null"));
        this.fallbackReasons = new java.util.ArrayList<>(Objects.requireNonNull(fallbackReasons,
                "fallbackReasons must not be null"));
    }

    /**
     * Returns the query source name from the SQL-like plan preview.
     *
     * @return source name
     */
    public String source() {
        return source;
    }

    /**
     * Returns the high-level pushdown mode.
     *
     * @return pushdown mode
     */
    public SqlLikePushdownMode mode() {
        return mode;
    }

    /**
     * Returns stages that are safe for a host adapter to apply first.
     *
     * @return pushable stage names
     */
    public List<String> pushableStages() {
        return java.util.Collections.unmodifiableList(pushableStages);
    }

    /**
     * Returns stages that must remain in PojoLens in-memory execution.
     *
     * @return in-memory stage names
     */
    public List<String> inMemoryStages() {
        return java.util.Collections.unmodifiableList(inMemoryStages);
    }

    /**
     * Returns stable reason codes explaining why the plan is not fully pushable.
     *
     * @return fallback reason codes
     */
    public List<String> fallbackReasons() {
        return java.util.Collections.unmodifiableList(fallbackReasons);
    }

    /**
     * Returns true when the query is fully inside the first-phase pushdown subset.
     *
     * @return true for fully pushable query shapes
     */
    public boolean isFullyPushable() {
        return mode == SqlLikePushdownMode.FULL;
    }

    /**
     * Returns true when a host adapter can push down some early work and then
     * finish remaining stages in memory.
     *
     * @return true for split execution shapes
     */
    public boolean requiresSplitExecution() {
        return mode == SqlLikePushdownMode.SPLIT;
    }

    /**
     * Returns true when no stage is currently safe to push into a host adapter.
     *
     * @return true for in-memory-only shapes
     */
    public boolean isInMemoryOnly() {
        return mode == SqlLikePushdownMode.IN_MEMORY_ONLY;
    }

    public SqlLikePushdownPreview copy() {
        return new SqlLikePushdownPreview(source, mode, pushableStages, inMemoryStages, fallbackReasons);
    }
}
