package laughing.man.commits.testing;

import laughing.man.commits.DatasetBundle;
import laughing.man.commits.sqllike.JoinBindings;
import laughing.man.commits.util.StringUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Named immutable dataset snapshot used by regression fixtures.
 */
public final class QuerySnapshotFixture {

    private final String name;
    private final DatasetBundle bundle;

    private QuerySnapshotFixture(String name, DatasetBundle bundle) {
        this.name = requireName(name);
        this.bundle = Objects.requireNonNull(bundle, "bundle must not be null");
    }

    public static QuerySnapshotFixture of(String name, List<?> primaryRows) {
        return new QuerySnapshotFixture(name, DatasetBundle.of(primaryRows));
    }

    public static QuerySnapshotFixture of(String name, List<?> primaryRows, Map<String, List<?>> joinSources) {
        return new QuerySnapshotFixture(name, DatasetBundle.of(primaryRows, joinSources));
    }

    public static QuerySnapshotFixture of(String name, List<?> primaryRows, JoinBindings joinBindings) {
        return new QuerySnapshotFixture(name, DatasetBundle.of(primaryRows, joinBindings));
    }

    public static QuerySnapshotFixture of(String name, DatasetBundle bundle) {
        return new QuerySnapshotFixture(name, bundle);
    }

    public String name() {
        return name;
    }

    public List<?> primaryRows() {
        return bundle.primaryRows();
    }

    public Map<String, List<?>> joinSources() {
        return bundle.joinSources();
    }

    public JoinBindings joinBindings() {
        return bundle.joinBindings();
    }

    public DatasetBundle bundle() {
        return bundle;
    }

    public boolean hasJoinSources() {
        return bundle.hasJoinSources();
    }

    private static String requireName(String value) {
        if (StringUtil.isNullOrBlank(value)) {
            throw new IllegalArgumentException("name must not be null/blank");
        }
        return value;
    }
}

