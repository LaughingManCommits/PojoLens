package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLens;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.chart.NullPointPolicy;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.filter.Filter;
import laughing.man.commits.sqllike.SqlLikeQuery;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class ChartVisualizationJmhBenchmark {

    @Param({"1000", "10000", "100000"})
    public int size;

    private List<ChartBenchmarkEvent> source;
    private ChartSpec barSpec;
    private ChartSpec lineSpec;
    private ChartSpec pieSpec;
    private ChartSpec areaSpec;
    private ChartSpec scatterSpec;
    private String barSql;
    private String lineSql;
    private String pieSql;
    private String areaSql;
    private String scatterSql;
    private ChartData barData;
    private ChartData lineData;
    private ChartData pieData;
    private ChartData areaData;
    private ChartData scatterData;
    private Filter barFilter;
    private Filter lineFilter;
    private Filter pieFilter;
    private Filter areaFilter;
    private Filter scatterFilter;
    private SqlLikeQuery parsedBarSql;
    private SqlLikeQuery parsedLineSql;
    private SqlLikeQuery parsedPieSql;
    private SqlLikeQuery parsedAreaSql;
    private SqlLikeQuery parsedScatterSql;

    @Setup
    public void setup() {
        source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            source.add(new ChartBenchmarkEvent(
                    departmentFor(i),
                    periodFor(i),
                    seriesFor(i),
                    amountFor(i),
                    xFor(i),
                    yFor(i)
            ));
        }

        barSpec = ChartSpec.of(ChartType.BAR, "department", "total").withSortedLabels(true);
        lineSpec = ChartSpec.of(ChartType.LINE, "period", "total", "series").withSortedLabels(true);
        pieSpec = ChartSpec.of(ChartType.PIE, "department", "total").withSortedLabels(true);
        areaSpec = ChartSpec.of(ChartType.AREA, "period", "total", "series")
                .withSortedLabels(true)
                .withStacked(true)
                .withPercentStacked(true)
                .withNullPointPolicy(NullPointPolicy.ZERO);
        scatterSpec = ChartSpec.of(ChartType.SCATTER, "xValue", "yValue", "series");

        barSql = "select department, sum(amount) as total group by department";
        lineSql = "select period, series, sum(amount) as total group by period, series";
        pieSql = "select department, sum(amount) as total group by department";
        areaSql = "select period, series, sum(amount) as total group by period, series";
        scatterSql = "select xValue, yValue, series";

        barFilter = PojoLens.newQueryBuilder(source)
                .addGroup("department")
                .addMetric("amount", Metric.SUM, "total")
                .initFilter();
        lineFilter = PojoLens.newQueryBuilder(source)
                .addGroup("period")
                .addGroup("series")
                .addMetric("amount", Metric.SUM, "total")
                .initFilter();
        pieFilter = PojoLens.newQueryBuilder(source)
                .addGroup("department")
                .addMetric("amount", Metric.SUM, "total")
                .initFilter();
        areaFilter = PojoLens.newQueryBuilder(source)
                .addGroup("period")
                .addGroup("series")
                .addMetric("amount", Metric.SUM, "total")
                .initFilter();
        scatterFilter = PojoLens.newQueryBuilder(source)
                .initFilter();

        parsedBarSql = PojoLens.parse(barSql);
        parsedLineSql = PojoLens.parse(lineSql);
        parsedPieSql = PojoLens.parse(pieSql);
        parsedAreaSql = PojoLens.parse(areaSql);
        parsedScatterSql = PojoLens.parse(scatterSql);

        barData = barFilter.chart(CategoryTotalRow.class, barSpec);
        lineData = lineFilter.chart(PeriodSeriesTotalRow.class, lineSpec);
        pieData = pieFilter.chart(CategoryTotalRow.class, pieSpec);
        areaData = areaFilter.chart(PeriodSeriesTotalRow.class, areaSpec);
        scatterData = scatterFilter.chart(ScatterRow.class, scatterSpec);
    }

    @Benchmark
    public ChartData fluentBarMapping() {
        return barFilter.chart(CategoryTotalRow.class, barSpec);
    }

    @Benchmark
    public ChartData sqlLikeBarMapping() {
        return parsedBarSql.chart(source, CategoryTotalRow.class, barSpec);
    }

    @Benchmark
    public ChartData fluentLineMapping() {
        return lineFilter.chart(PeriodSeriesTotalRow.class, lineSpec);
    }

    @Benchmark
    public ChartData sqlLikeLineMapping() {
        return parsedLineSql.chart(source, PeriodSeriesTotalRow.class, lineSpec);
    }

    @Benchmark
    public ChartData fluentPieMapping() {
        return pieFilter.chart(CategoryTotalRow.class, pieSpec);
    }

    @Benchmark
    public ChartData sqlLikePieMapping() {
        return parsedPieSql.chart(source, CategoryTotalRow.class, pieSpec);
    }

    @Benchmark
    public ChartData fluentAreaMapping() {
        return areaFilter.chart(PeriodSeriesTotalRow.class, areaSpec);
    }

    @Benchmark
    public ChartData sqlLikeAreaMapping() {
        return parsedAreaSql.chart(source, PeriodSeriesTotalRow.class, areaSpec);
    }

    @Benchmark
    public ChartData fluentScatterMapping() {
        return scatterFilter.chart(ScatterRow.class, scatterSpec);
    }

    @Benchmark
    public ChartData sqlLikeScatterMapping() {
        return parsedScatterSql.chart(source, ScatterRow.class, scatterSpec);
    }

    @Benchmark
    public String barPayloadJsonExport() {
        return ChartPayloadJsonExporter.toJson(barData);
    }

    @Benchmark
    public String linePayloadJsonExport() {
        return ChartPayloadJsonExporter.toJson(lineData);
    }

    @Benchmark
    public String piePayloadJsonExport() {
        return ChartPayloadJsonExporter.toJson(pieData);
    }

    @Benchmark
    public String areaPayloadJsonExport() {
        return ChartPayloadJsonExporter.toJson(areaData);
    }

    @Benchmark
    public String scatterPayloadJsonExport() {
        return ChartPayloadJsonExporter.toJson(scatterData);
    }

    private static String departmentFor(int i) {
        String[] departments = new String[] {"Engineering", "Finance", "Operations", "Support", "Sales"};
        return departments[BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 700L, i, departments.length)];
    }

    private static String periodFor(int i) {
        int month = (i % 24) + 1;
        int year = 2024 + ((month - 1) / 12);
        int normalizedMonth = ((month - 1) % 12) + 1;
        return year + "-" + (normalizedMonth < 10 ? "0" + normalizedMonth : String.valueOf(normalizedMonth));
    }

    private static String seriesFor(int i) {
        String[] series = new String[] {"Actual", "Plan", "Forecast"};
        return series[BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 701L, i, series.length)];
    }

    private static double amountFor(int i) {
        int base = 80_000 + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 702L, i, 320_000);
        return base + (i % 17) * 45.0;
    }

    private static double xFor(int i) {
        return BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 703L, i, 10_000) / 10.0;
    }

    private static double yFor(int i) {
        double trend = 20.0 + (i % 300) * 0.35;
        double noise = BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 704L, i, 80) / 20.0;
        return trend + noise;
    }

    public static class ChartBenchmarkEvent {
        public String department;
        public String period;
        public String series;
        public double amount;
        public double xValue;
        public double yValue;

        public ChartBenchmarkEvent() {
        }

        public ChartBenchmarkEvent(String department,
                                   String period,
                                   String series,
                                   double amount,
                                   double xValue,
                                   double yValue) {
            this.department = department;
            this.period = period;
            this.series = series;
            this.amount = amount;
            this.xValue = xValue;
            this.yValue = yValue;
        }
    }

    public static class CategoryTotalRow {
        public String department;
        public double total;

        public CategoryTotalRow() {
        }
    }

    public static class PeriodSeriesTotalRow {
        public String period;
        public String series;
        public double total;

        public PeriodSeriesTotalRow() {
        }
    }

    public static class ScatterRow {
        public double xValue;
        public double yValue;
        public String series;

        public ScatterRow() {
        }
    }
}

