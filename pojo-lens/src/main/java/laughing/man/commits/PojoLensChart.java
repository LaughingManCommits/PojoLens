package laughing.man.commits;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartResultMapper;
import laughing.man.commits.chart.ChartSpec;

import java.util.List;

/**
 * Chart mapping entry points.
 */
public final class PojoLensChart {

    private PojoLensChart() {
    }

    public static <T> ChartData toChartData(List<T> rows, ChartSpec spec) {
        return ChartResultMapper.toChartData(rows, spec);
    }
}

