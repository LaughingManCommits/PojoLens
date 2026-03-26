package laughing.man.commits.benchmark;

import laughing.man.commits.PojoLensCore;
import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLens;
import laughing.man.commits.enums.Clauses;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Query helpers for benchmark metric rows via fluent and SQL-like APIs.
 */
public final class BenchmarkMetricQueries {

    private BenchmarkMetricQueries() {
    }

    public static List<BenchmarkMetricRow> fluentByChartTypeAndMinSize(List<BenchmarkMetricRow> rows,
                                                                        String chartType,
                                                                        int minSize) {
        List<BenchmarkMetricRow> filtered = PojoLensCore.newQueryBuilder(rows)
                .addRule("chartType", chartType, Clauses.EQUAL)
                .addRule("size", minSize, Clauses.BIGGER_EQUAL)
                .initFilter()
                .filter(BenchmarkMetricRow.class);
        filtered.sort(Comparator.comparingDouble(r -> r.score));
        return filtered;
    }

    public static List<BenchmarkMetricRow> sqlLikeByChartTypeAndMinSize(List<BenchmarkMetricRow> rows,
                                                                         String chartType,
                                                                         int minSize) {
        String query = "where chartType = '" + chartType + "' and size >= " + minSize + " order by score asc";
        return PojoLensSql.parse(query).filter(rows, BenchmarkMetricRow.class);
    }

    public static List<BenchmarkMetricRow> fluentFailures(List<BenchmarkMetricRow> rows, int limit) {
        List<BenchmarkMetricRow> filtered = PojoLensCore.newQueryBuilder(rows)
                .addRule("status", "FAIL", Clauses.EQUAL)
                .limit(limit)
                .initFilter()
                .filter(BenchmarkMetricRow.class);
        filtered.sort(Comparator.comparingDouble((BenchmarkMetricRow r) -> r.delta).reversed());
        return filtered;
    }

    public static List<BenchmarkMetricRow> sqlLikeFailures(List<BenchmarkMetricRow> rows, int limit) {
        String query = "where status = 'FAIL' order by delta desc limit " + limit;
        return PojoLensSql.parse(query).filter(rows, BenchmarkMetricRow.class);
    }

    public static List<BenchmarkMetricRow> fluentMappingStage(List<BenchmarkMetricRow> rows) {
        return fluentByFamilyAndStage(rows, "FLUENT", "MAPPING");
    }

    public static List<BenchmarkMetricRow> sqlLikeMappingStage(List<BenchmarkMetricRow> rows) {
        return fluentByFamilyAndStage(rows, "SQLLIKE", "MAPPING");
    }

    public static List<BenchmarkMetricRow> fluentByFamilyAndStage(List<BenchmarkMetricRow> rows,
                                                                  String family,
                                                                  String metricStage) {
        return PojoLensCore.newQueryBuilder(rows)
                .addRule("family", family, Clauses.EQUAL)
                .addRule("metricStage", metricStage, Clauses.EQUAL)
                .initFilter()
                .filter(BenchmarkMetricRow.class);
    }

    public static List<BenchmarkMetricRow> sqlLikeByFamilyAndStage(List<BenchmarkMetricRow> rows,
                                                                   String family,
                                                                   String metricStage) {
        return PojoLensSql.parse("where family = '" + family + "' and metricStage = '" + metricStage + "'")
                .filter(rows, BenchmarkMetricRow.class);
    }

    public static List<String> keys(List<BenchmarkMetricRow> rows) {
        List<String> keys = new ArrayList<>();
        for (BenchmarkMetricRow row : rows) {
            keys.add(row.benchmarkKey);
        }
        return keys;
    }
}


