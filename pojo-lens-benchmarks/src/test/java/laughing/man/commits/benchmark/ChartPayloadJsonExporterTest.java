package laughing.man.commits.benchmark;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartDataset;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chart.NullPointPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChartPayloadJsonExporterTest {

    @Test
    public void toJsonShouldSerializeEscapedStringsAndFixedScaleNumbers() {
        ChartData chartData = new ChartData();
        chartData.setType(ChartType.LINE);
        chartData.setTitle("Revenue \"Plan\"");
        chartData.setXLabel("period");
        chartData.setYLabel("value\\usd");
        chartData.setStacked(true);
        chartData.setPercentStacked(false);
        chartData.setNullPointPolicy(NullPointPolicy.ZERO);
        chartData.setLabels(Arrays.asList("2025-01", null, "Q\"1", "2025-04"));
        chartData.setDatasets(List.of(
                new ChartDataset(
                        "Actual",
                        Arrays.asList(1d, 2.5d, 1.23456789d, null),
                        "#00ff00",
                        "stack-1",
                        "left"
                ),
                new ChartDataset(
                        "Plan\\Alt",
                        Arrays.asList(-0.125d, null, 3d, 4.2d),
                        null,
                        null,
                        null
                )
        ));

        assertEquals(
                "{\"type\":\"LINE\",\"title\":\"Revenue \\\"Plan\\\"\",\"xLabel\":\"period\",\"yLabel\":\"value\\\\usd\","
                        + "\"stacked\":true,\"percentStacked\":false,\"nullPointPolicy\":\"ZERO\","
                        + "\"labels\":[\"2025-01\",null,\"Q\\\"1\",\"2025-04\"],"
                        + "\"datasets\":["
                        + "{\"label\":\"Actual\",\"colorHint\":\"#00ff00\",\"stackGroupId\":\"stack-1\",\"axisId\":\"left\","
                        + "\"values\":[1.000000,2.500000,1.234568,null]},"
                        + "{\"label\":\"Plan\\\\Alt\",\"colorHint\":null,\"stackGroupId\":null,\"axisId\":null,"
                        + "\"values\":[-0.125000,null,3.000000,4.200000]}"
                        + "]}",
                ChartPayloadJsonExporter.toJson(chartData)
        );
    }
}
