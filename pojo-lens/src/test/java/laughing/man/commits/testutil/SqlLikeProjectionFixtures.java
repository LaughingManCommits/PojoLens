package laughing.man.commits.testutil;

public final class SqlLikeProjectionFixtures {

    private SqlLikeProjectionFixtures() {
    }

    public static class ComputedBoostProjection {
        public String name;
        public double boosted;

        public ComputedBoostProjection() {
        }
    }

    public static class ComputedScalarProjection {
        public double x;

        public ComputedScalarProjection() {
        }
    }
}


