package laughing.man.commits.benchmark;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartDataset;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight deterministic serializer used by chart benchmark export paths.
 */
public final class ChartPayloadJsonExporter {

    private ChartPayloadJsonExporter() {
    }

    public static String toJson(ChartData chartData) {
        StringBuilder sb = new StringBuilder(2048);
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
                        sb.append(String.format(Locale.ROOT, "%.6f", value));
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
}

