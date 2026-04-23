package laughing.man.commits.sqllike;

import java.util.List;
import java.util.Objects;

/**
 * Immutable request metadata passed to a host-owned pushdown adapter.
 */
public final class SqlLikePushdownRequest {

    private final String source;
    private final String queryText;
    private final SqlLikePushdownPreview preview;

    public SqlLikePushdownRequest(String source, String queryText, SqlLikePushdownPreview preview) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.queryText = Objects.requireNonNull(queryText, "queryText must not be null");
        this.preview = Objects.requireNonNull(preview, "preview must not be null");
    }

    /**
     * Returns the source name parsed from the query text.
     *
     * @return source name
     */
    public String source() {
        return source;
    }

    /**
     * Returns the normalized SQL-like query text.
     *
     * @return normalized query text
     */
    public String queryText() {
        return queryText;
    }

    /**
     * Returns deterministic pushdown-readiness metadata.
     *
     * @return pushdown preview
     */
    public SqlLikePushdownPreview preview() {
        return preview;
    }

    /**
     * Returns stages PojoLens classified as safe for first-phase host work.
     *
     * @return pushable stages
     */
    public List<String> requestedStages() {
        return preview.pushableStages();
    }

    /**
     * Returns true when the named stage was requested for first-phase pushdown.
     *
     * @param stage stage name such as {@code WHERE}
     * @return true when requested
     */
    public boolean requestsStage(String stage) {
        return stage != null && preview.pushableStages().contains(stage);
    }
}
