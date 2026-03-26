package laughing.man.commits.chart;

/**
 * Chart-friendly multi-series point shape.
 */
public final class MultiSeriesPoint {

    private String x;
    private String series;
    private double value;

    public MultiSeriesPoint(String x, String series, double value) {
        this.x = x;
        this.series = series;
        this.value = value;
    }

    public String getX() {
        return x;
    }

    public void setX(String x) {
        this.x = x;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}

