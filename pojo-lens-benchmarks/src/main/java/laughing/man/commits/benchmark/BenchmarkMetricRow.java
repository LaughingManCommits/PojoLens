package laughing.man.commits.benchmark;

/**
 * Normalized benchmark metric row used for querying and plotting.
 */
public class BenchmarkMetricRow {
    public String benchmarkKey;
    public String benchmarkName;
    public String methodName;
    public String source;
    public String family;
    public String benchmarkCategory;
    public String chartType;
    public String metricStage;
    public String unit;
    public String status;
    public int size;
    public double score;
    public double threshold;
    public double delta;
    public double ratioToThreshold;
    public boolean thresholdPresent;
    public boolean pass;

    public BenchmarkMetricRow() {
    }
}

