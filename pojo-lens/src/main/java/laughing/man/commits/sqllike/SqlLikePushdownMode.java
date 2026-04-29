package laughing.man.commits.sqllike;

/**
 * High-level pushdown classification for a SQL-like query shape.
 */
public enum SqlLikePushdownMode {
    /**
     * The declared query shape fits the first-phase host-adapter subset.
     */
    FULL,

    /**
     * At least one early stage can be handled by a host adapter, but one or
     * more later stages must still run in the in-memory engine.
     */
    SPLIT,

    /**
     * The query shape has no currently safe host-adapter pushdown stage.
     */
    IN_MEMORY_ONLY
}
