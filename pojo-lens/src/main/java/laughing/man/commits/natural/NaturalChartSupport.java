package laughing.man.commits.natural;

import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.ast.SelectAst;
import laughing.man.commits.sqllike.ast.SelectFieldAst;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NaturalChartSupport {

    private NaturalChartSupport() {
    }

    static ChartSpec inferChartSpec(QueryAst ast, ChartType chartType) {
        if (chartType == null) {
            throw new IllegalArgumentException(
                    "Natural query does not declare a chart phrase; add 'as <type> chart' or pass ChartSpec explicitly"
            );
        }
        ChartShape shape = inferShape(ast);
        if (chartType == ChartType.PIE && shape.seriesField() != null) {
            throw new IllegalArgumentException("Natural pie-chart inference requires exactly two SHOW outputs");
        }
        return shape.seriesField() == null
                ? ChartSpec.of(chartType, shape.xField(), shape.yField())
                : ChartSpec.of(chartType, shape.xField(), shape.yField(), shape.seriesField());
    }

    static Map<String, Object> describeInferredChart(QueryAst ast, ChartType chartType) {
        if (chartType == null) {
            return Map.of();
        }
        ChartSpec spec = inferChartSpec(ast, chartType);
        LinkedHashMap<String, Object> description = new LinkedHashMap<>();
        description.put("type", chartType.name());
        description.put("xField", spec.xField());
        description.put("yField", spec.yField());
        if (spec.seriesField() != null) {
            description.put("seriesField", spec.seriesField());
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(description));
    }

    private static ChartShape inferShape(QueryAst ast) {
        if (ast == null) {
            throw new IllegalArgumentException("Natural chart inference requires a parsed query");
        }
        SelectAst select = ast.select();
        if (select == null || select.wildcard()) {
            throw new IllegalArgumentException(
                    "Natural chart inference requires explicit SHOW outputs; wildcard SHOW is not supported"
            );
        }
        List<SelectFieldAst> fields = select.fields();
        if (fields.size() == 2) {
            return new ChartShape(fields.get(0).outputName(), fields.get(1).outputName(), null);
        }
        if (fields.size() == 3) {
            return new ChartShape(fields.get(0).outputName(), fields.get(2).outputName(), fields.get(1).outputName());
        }
        throw new IllegalArgumentException(
                "Natural chart inference requires exactly 2 SHOW outputs (x,y) or 3 SHOW outputs (x,series,y)"
        );
    }

    private record ChartShape(String xField, String yField, String seriesField) {
    }
}
