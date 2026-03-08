package laughing.man.commits.chart;

import java.util.ArrayList;
import java.util.List;

/**
 * Frontend-ready chart data payload.
 */
public final class ChartData {

    private ChartType type;
    private String title;
    private String xLabel;
    private String yLabel;
    private boolean stacked;
    private boolean percentStacked;
    private NullPointPolicy nullPointPolicy;
    private List<String> labels;
    private List<ChartDataset> datasets;

    public ChartData() {
        this.nullPointPolicy = NullPointPolicy.PRESERVE;
        this.labels = new ArrayList<>();
        this.datasets = new ArrayList<>();
    }

    public ChartData(ChartType type,
                     String title,
                     String xLabel,
                     String yLabel,
                     boolean stacked,
                     boolean percentStacked,
                     NullPointPolicy nullPointPolicy,
                     List<String> labels,
                     List<ChartDataset> datasets) {
        this.type = type;
        this.title = title;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        this.stacked = stacked;
        this.percentStacked = percentStacked;
        this.nullPointPolicy = nullPointPolicy == null ? NullPointPolicy.PRESERVE : nullPointPolicy;
        this.labels = labels == null ? new ArrayList<>() : new ArrayList<>(labels);
        this.datasets = datasets == null ? new ArrayList<>() : new ArrayList<>(datasets);
    }

    public ChartType getType() {
        return type;
    }

    public void setType(ChartType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getXLabel() {
        return xLabel;
    }

    public void setXLabel(String xLabel) {
        this.xLabel = xLabel;
    }

    public String getYLabel() {
        return yLabel;
    }

    public void setYLabel(String yLabel) {
        this.yLabel = yLabel;
    }

    public boolean isStacked() {
        return stacked;
    }

    public void setStacked(boolean stacked) {
        this.stacked = stacked;
    }

    public boolean isPercentStacked() {
        return percentStacked;
    }

    public void setPercentStacked(boolean percentStacked) {
        this.percentStacked = percentStacked;
    }

    public NullPointPolicy getNullPointPolicy() {
        return nullPointPolicy;
    }

    public void setNullPointPolicy(NullPointPolicy nullPointPolicy) {
        this.nullPointPolicy = nullPointPolicy == null ? NullPointPolicy.PRESERVE : nullPointPolicy;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels == null ? new ArrayList<>() : new ArrayList<>(labels);
    }

    public List<ChartDataset> getDatasets() {
        return datasets;
    }

    public void setDatasets(List<ChartDataset> datasets) {
        this.datasets = datasets == null ? new ArrayList<>() : new ArrayList<>(datasets);
    }
}

