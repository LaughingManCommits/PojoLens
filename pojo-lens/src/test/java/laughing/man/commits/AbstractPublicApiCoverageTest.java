package laughing.man.commits;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractPublicApiCoverageTest {

    @BeforeEach
    void setUpSqlLikeCacheDefaults() {
        applySqlLikeCacheDefaults();
    }

    @AfterEach
    void tearDownSqlLikeCacheDefaults() {
        applySqlLikeCacheDefaults();
    }

    private static void applySqlLikeCacheDefaults() {
        PojoLens.setSqlLikeCacheEnabled(true);
        PojoLens.setSqlLikeCacheStatsEnabled(true);
        PojoLens.setSqlLikeCacheMaxEntries(256);
        PojoLens.setSqlLikeCacheMaxWeight(0L);
        PojoLens.setSqlLikeCacheExpireAfterWriteMillis(0L);
        PojoLens.clearSqlLikeCache();
        PojoLens.resetSqlLikeCacheStats();
    }
}
