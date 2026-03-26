package laughing.man.commits.spring.boot.autoconfigure;

import laughing.man.commits.PojoLensRuntimePreset;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot configuration properties for PojoLens runtime behavior.
 */
@ConfigurationProperties(prefix = "pojo-lens")
public class PojoLensProperties {

    private PojoLensRuntimePreset preset = PojoLensRuntimePreset.PROD;
    private Boolean strictParameterTypes;
    private Boolean lintMode;
    private final CacheProperties sqlLikeCache = new CacheProperties();
    private final CacheProperties statsPlanCache = new CacheProperties();
    private final TelemetryProperties telemetry = new TelemetryProperties();

    public PojoLensRuntimePreset getPreset() {
        return preset;
    }

    public void setPreset(PojoLensRuntimePreset preset) {
        this.preset = preset;
    }

    public Boolean getStrictParameterTypes() {
        return strictParameterTypes;
    }

    public void setStrictParameterTypes(Boolean strictParameterTypes) {
        this.strictParameterTypes = strictParameterTypes;
    }

    public Boolean getLintMode() {
        return lintMode;
    }

    public void setLintMode(Boolean lintMode) {
        this.lintMode = lintMode;
    }

    public CacheProperties getSqlLikeCache() {
        return sqlLikeCache;
    }

    public CacheProperties getStatsPlanCache() {
        return statsPlanCache;
    }

    public TelemetryProperties getTelemetry() {
        return telemetry;
    }

    public static final class CacheProperties {

        private Boolean enabled;
        private Boolean statsEnabled;
        private Integer maxEntries;
        private Long maxWeight;
        private Long expireAfterWriteMillis;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getStatsEnabled() {
            return statsEnabled;
        }

        public void setStatsEnabled(Boolean statsEnabled) {
            this.statsEnabled = statsEnabled;
        }

        public Integer getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(Integer maxEntries) {
            this.maxEntries = maxEntries;
        }

        public Long getMaxWeight() {
            return maxWeight;
        }

        public void setMaxWeight(Long maxWeight) {
            this.maxWeight = maxWeight;
        }

        public Long getExpireAfterWriteMillis() {
            return expireAfterWriteMillis;
        }

        public void setExpireAfterWriteMillis(Long expireAfterWriteMillis) {
            this.expireAfterWriteMillis = expireAfterWriteMillis;
        }
    }

    public static final class TelemetryProperties {

        private final MicrometerProperties micrometer = new MicrometerProperties();

        public MicrometerProperties getMicrometer() {
            return micrometer;
        }
    }

    public static final class MicrometerProperties {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
