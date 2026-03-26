package laughing.man.commits.publicapi;

import laughing.man.commits.PojoLens;
import laughing.man.commits.PojoLensRuntime;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractPublicApiCoverageTest {

    protected PojoLensRuntime runtime;

    @BeforeEach
    void setUpRuntimeDefaults() {
        runtime = PojoLens.newRuntime();
        runtime.sqlLikeCache().setEnabled(true);
        runtime.sqlLikeCache().setStatsEnabled(true);
        runtime.sqlLikeCache().setMaxEntries(256);
        runtime.sqlLikeCache().setMaxWeight(0L);
        runtime.sqlLikeCache().setExpireAfterWriteMillis(0L);
        runtime.sqlLikeCache().clear();
        runtime.sqlLikeCache().resetStats();

        runtime.statsPlanCache().setEnabled(true);
        runtime.statsPlanCache().setStatsEnabled(true);
        runtime.statsPlanCache().setMaxEntries(512);
        runtime.statsPlanCache().setMaxWeight(0L);
        runtime.statsPlanCache().setExpireAfterWriteMillis(0L);
        runtime.statsPlanCache().clear();
        runtime.statsPlanCache().resetStats();
    }
}
