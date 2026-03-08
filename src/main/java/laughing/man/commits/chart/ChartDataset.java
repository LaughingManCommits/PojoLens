package laughing.man.commits.chart;

import java.util.ArrayList;
import java.util.List;

/**
 * Series payload for chart output.
 */
public final class ChartDataset {

    private String label;
    private List<Double> values;
    private String colorHint;
    private String stackGroupId;
    private String axisId;

    public ChartDataset() {
        this.values = new ArrayList<>();
    }

    public ChartDataset(String label, List<Double> values) {
        this(label, values, null, null, null);
    }

    public ChartDataset(String label,
                        List<Double> values,
                        String colorHint,
                        String stackGroupId,
                        String axisId) {
        this.label = label;
        this.values = values == null ? new ArrayList<>() : new ArrayList<>(values);
        this.colorHint = colorHint;
        this.stackGroupId = stackGroupId;
        this.axisId = axisId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<Double> getValues() {
        return values;
    }

    public void setValues(List<Double> values) {
        this.values = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    public String getColorHint() {
        return colorHint;
    }

    public void setColorHint(String colorHint) {
        this.colorHint = colorHint;
    }

    public String getStackGroupId() {
        return stackGroupId;
    }

    public void setStackGroupId(String stackGroupId) {
        this.stackGroupId = stackGroupId;
    }

    public String getAxisId() {
        return axisId;
    }

    public void setAxisId(String axisId) {
        this.axisId = axisId;
    }
}

