package laughing.man.commits.benchmark;

public final class BenchmarkProfiles {

    public static final String PROFILE_ID = "deterministic-v1";
    public static final long BASE_EPOCH_MILLIS = 1700000000000L;
    public static final long STATS_BASE_EPOCH_MILLIS = 1735689600000L;
    public static final long DATA_SEED = 20260301L;

    private BenchmarkProfiles() {
    }

    public static int deterministicInt(long seed, int index, int mod) {
        if (mod <= 0) {
            throw new IllegalArgumentException("mod must be > 0");
        }
        long x = seed + (index * 1103515245L) + 12345L;
        int value = (int) (x & 0x7fffffff);
        return value % mod;
    }

    public static String profileSummary() {
        return "profile=" + PROFILE_ID
                + ",seed=" + DATA_SEED
                + ",baseEpoch=" + BASE_EPOCH_MILLIS
                + ",statsBaseEpoch=" + STATS_BASE_EPOCH_MILLIS;
    }
}

