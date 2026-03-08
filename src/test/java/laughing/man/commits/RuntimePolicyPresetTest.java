package laughing.man.commits;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RuntimePolicyPresetTest {

    @Test
    public void devPresetShouldFavorDiagnosticsAndVisibleCacheStats() {
        PojoLensRuntime runtime = PojoLens.newRuntime(PojoLensRuntimePreset.DEV);

        assertTrue(runtime.sqlLikeCache().isEnabled());
        assertTrue(runtime.sqlLikeCache().isStatsEnabled());
        assertEquals(256, runtime.sqlLikeCache().getMaxEntries());
        assertEquals(0L, runtime.sqlLikeCache().getExpireAfterWriteMillis());

        assertTrue(runtime.statsPlanCache().isEnabled());
        assertTrue(runtime.statsPlanCache().isStatsEnabled());
        assertEquals(512, runtime.statsPlanCache().maxEntries());
        assertEquals(0L, runtime.statsPlanCache().expireAfterWriteMillis());

        assertTrue(runtime.isStrictParameterTypes());
        assertTrue(runtime.isLintMode());
    }

    @Test
    public void prodPresetShouldFavorBoundedCachesAndLowerOverhead() {
        PojoLensRuntime runtime = PojoLens.newRuntime(PojoLensRuntimePreset.PROD);

        assertTrue(runtime.sqlLikeCache().isEnabled());
        assertFalse(runtime.sqlLikeCache().isStatsEnabled());
        assertEquals(1024, runtime.sqlLikeCache().getMaxEntries());
        assertEquals(300000L, runtime.sqlLikeCache().getExpireAfterWriteMillis());

        assertTrue(runtime.statsPlanCache().isEnabled());
        assertFalse(runtime.statsPlanCache().isStatsEnabled());
        assertEquals(1024, runtime.statsPlanCache().maxEntries());
        assertEquals(300000L, runtime.statsPlanCache().expireAfterWriteMillis());

        assertFalse(runtime.isStrictParameterTypes());
        assertFalse(runtime.isLintMode());
    }

    @Test
    public void testPresetShouldDisableCachesAndEnableStrictDiagnostics() {
        PojoLensRuntime runtime = PojoLens.newRuntime().applyPreset(PojoLensRuntimePreset.TEST);

        assertFalse(runtime.sqlLikeCache().isEnabled());
        assertFalse(runtime.sqlLikeCache().isStatsEnabled());
        assertEquals(0, runtime.sqlLikeCache().getSize());

        assertFalse(runtime.statsPlanCache().isEnabled());
        assertFalse(runtime.statsPlanCache().isStatsEnabled());
        assertEquals(0, runtime.statsPlanCache().size());

        assertTrue(runtime.isStrictParameterTypes());
        assertTrue(runtime.isLintMode());
    }

    @Test
    public void manualOverridesShouldRemainAvailableAfterApplyingPreset() {
        PojoLensRuntime runtime = PojoLens.newRuntime(PojoLensRuntimePreset.PROD);

        runtime.setLintMode(true);
        runtime.setStrictParameterTypes(true);
        runtime.sqlLikeCache().setStatsEnabled(true);
        runtime.statsPlanCache().setEnabled(false);

        assertTrue(runtime.isLintMode());
        assertTrue(runtime.isStrictParameterTypes());
        assertTrue(runtime.sqlLikeCache().isStatsEnabled());
        assertFalse(runtime.statsPlanCache().isEnabled());
    }
}

