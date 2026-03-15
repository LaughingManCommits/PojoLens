package laughing.man.commits.benchmark;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartDataset;

import java.util.List;

/**
 * Lightweight deterministic serializer used by chart benchmark export paths.
 */
public final class ChartPayloadJsonExporter {

    private static final long FIXED_SCALE = 1_000_000L;
    private static final double MAX_FIXED_SCALE_ABS = Long.MAX_VALUE / (double) FIXED_SCALE;

    private ChartPayloadJsonExporter() {
    }

    public static String toJson(ChartData chartData) {
        StringBuilder sb = new StringBuilder(estimatedCapacity(chartData));
        sb.append("{");
        appendStringField(sb, "type", chartData.getType() == null ? null : chartData.getType().name(), true);
        appendStringField(sb, "title", chartData.getTitle(), true);
        appendStringField(sb, "xLabel", chartData.getXLabel(), true);
        appendStringField(sb, "yLabel", chartData.getYLabel(), true);
        sb.append("\"stacked\":").append(chartData.isStacked()).append(",");
        sb.append("\"percentStacked\":").append(chartData.isPercentStacked()).append(",");
        appendStringField(sb, "nullPointPolicy",
                chartData.getNullPointPolicy() == null ? null : chartData.getNullPointPolicy().name(), true);
        appendLabels(sb, chartData.getLabels());
        sb.append(",");
        appendDatasets(sb, chartData.getDatasets());
        sb.append("}");
        return sb.toString();
    }

    private static void appendLabels(StringBuilder sb, List<String> labels) {
        sb.append("\"labels\":[");
        if (labels != null) {
            for (int i = 0; i < labels.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                appendQuoted(sb, labels.get(i));
            }
        }
        sb.append("]");
    }

    private static void appendDatasets(StringBuilder sb, List<ChartDataset> datasets) {
        sb.append("\"datasets\":[");
        if (datasets != null) {
            for (int i = 0; i < datasets.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                ChartDataset dataset = datasets.get(i);
                sb.append("{");
                appendStringField(sb, "label", dataset.getLabel(), true);
                appendStringField(sb, "colorHint", dataset.getColorHint(), true);
                appendStringField(sb, "stackGroupId", dataset.getStackGroupId(), true);
                appendStringField(sb, "axisId", dataset.getAxisId(), true);
                sb.append("\"values\":[");
                List<Double> values = dataset.getValues();
                for (int j = 0; j < values.size(); j++) {
                    if (j > 0) {
                        sb.append(",");
                    }
                    Double value = values.get(j);
                    if (value == null) {
                        sb.append("null");
                    } else {
                        appendFixedScale(sb, value);
                    }
                }
                sb.append("]");
                sb.append("}");
            }
        }
        sb.append("]");
    }

    private static void appendStringField(StringBuilder sb, String key, String value, boolean trailingComma) {
        sb.append("\"").append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            appendQuoted(sb, value);
        }
        if (trailingComma) {
            sb.append(",");
        }
    }

    private static void appendQuoted(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append("\"");
    }

    private static int estimatedCapacity(ChartData chartData) {
        long capacity = 256L;
        List<String> labels = chartData.getLabels();
        if (labels != null) {
            capacity += (long) labels.size() * 16L;
        }
        List<ChartDataset> datasets = chartData.getDatasets();
        if (datasets != null) {
            capacity += (long) datasets.size() * 96L;
            for (ChartDataset dataset : datasets) {
                if (dataset != null && dataset.getValues() != null) {
                    capacity += (long) dataset.getValues().size() * 12L;
                }
            }
        }
        return capacity >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    private static void appendFixedScale(StringBuilder sb, double value) {
        if (!Double.isFinite(value)) {
            sb.append(Double.toString(value));
            return;
        }
        double absoluteValue = Math.abs(value);
        if (absoluteValue > MAX_FIXED_SCALE_ABS) {
            sb.append(Double.toString(value));
            return;
        }
        long scaled = Math.round(absoluteValue * FIXED_SCALE);
        if (Double.doubleToRawLongBits(value) < 0) {
            sb.append('-');
        }
        sb.append(scaled / FIXED_SCALE).append('.');
        appendFraction(sb, scaled % FIXED_SCALE);
    }

    private static void appendFraction(StringBuilder sb, long fraction) {
        if (fraction < 100000L) {
            sb.append('0');
        }
        if (fraction < 10000L) {
            sb.append('0');
        }
        if (fraction < 1000L) {
            sb.append('0');
        }
        if (fraction < 100L) {
            sb.append('0');
        }
        if (fraction < 10L) {
            sb.append('0');
        }
        sb.append(fraction);
    }
}

