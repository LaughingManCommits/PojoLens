package laughing.man.commits.benchmark;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the WP-18 to WP-23 and WP-29 semantics benchmarks compute the right answers, so their
 * timings measure correct work.
 */
public class CoreSemanticsBenchmarkParityTest {

    @ParameterizedTest
    @ValueSource(ints = {1000, 10000})
    public void temporalLiteralsMatchReferenceAndTypedValue(int size) {
        TemporalFilterJmhBenchmark benchmark = new TemporalFilterJmhBenchmark();
        benchmark.size = size;
        benchmark.setup();
        List<TemporalFilterJmhBenchmark.TemporalRow> rows = benchmark.rows();
        TemporalFilterJmhBenchmark.TemporalRow middle = rows.get(size / 2);
        LocalDate middleDay = middle.localAt.toLocalDate();
        LocalDateTime middleSecond = middle.localAt.truncatedTo(ChronoUnit.SECONDS);

        long dayReference = TemporalFilterJmhBenchmark.checksum(rows.stream()
                .filter(row -> !row.localAt.toLocalDate().isBefore(middleDay)).toList());
        long secondReference = TemporalFilterJmhBenchmark.checksum(rows.stream()
                .filter(row -> row.localAt.truncatedTo(ChronoUnit.SECONDS).isAfter(middleSecond)).toList());

        assertEquals(dayReference, benchmark.sqlLikeDayLiteral());
        assertEquals(secondReference, benchmark.sqlLikeSecondLiteral());
        assertEquals(benchmark.typedInstantValue(), benchmark.sqlLikeMillisLiteral());
        assertTrue(benchmark.typedInstantValue() > 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 10000})
    public void keysetPagesMatchManualSlices(int size) {
        KeysetPagingJmhBenchmark benchmark = new KeysetPagingJmhBenchmark();
        benchmark.size = size;
        benchmark.setup();
        List<KeysetPagingJmhBenchmark.PagedRow> rows = benchmark.rows();

        List<KeysetPagingJmhBenchmark.PagedRow> bySalary = new ArrayList<>(rows);
        bySalary.sort(Comparator.<KeysetPagingJmhBenchmark.PagedRow>comparingInt(row -> row.salary)
                .thenComparingInt(row -> row.id).reversed());
        long secondSalaryPage = KeysetPagingJmhBenchmark.checksum(bySalary.subList(50, 100));
        // keysetBefore(cursor at page 2's last row, index 99): the 50 rows before it.
        long beforeSecondPageEnd = KeysetPagingJmhBenchmark.checksum(bySalary.subList(49, 99));

        // Engine null placement: nulls last in DESC.
        List<KeysetPagingJmhBenchmark.PagedRow> byScore = new ArrayList<>(rows);
        byScore.sort(Comparator.<KeysetPagingJmhBenchmark.PagedRow, Integer>comparing(
                        row -> row.score, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(row -> row.id).reversed());
        long secondScorePage = KeysetPagingJmhBenchmark.checksum(byScore.subList(50, 100));

        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(
                row -> row.dept, LinkedHashMap::new, Collectors.counting()));
        List<Map.Entry<String, Long>> byCount = new ArrayList<>(counts.entrySet());
        byCount.sort(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        long secondDeptPage = 0L;
        for (Map.Entry<String, Long> entry : byCount.subList(5, 10)) {
            secondDeptPage = secondDeptPage * 31 + entry.getKey().hashCode() + entry.getValue();
        }

        assertEquals(secondSalaryPage, benchmark.plainNextPage());
        assertEquals(beforeSecondPageEnd, benchmark.plainPreviousPage());
        assertEquals(secondScorePage, benchmark.nullableNextPage());
        assertEquals(secondDeptPage, benchmark.aggregateAliasNextPage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 10000})
    public void streamSourcesLoadTheSameRowsAsPaths(int size) throws Exception {
        StreamLoadJmhBenchmark benchmark = new StreamLoadJmhBenchmark();
        benchmark.size = size;
        benchmark.setup();
        try {
            long csv = benchmark.csvFromPath();

            assertEquals(csv, benchmark.csvFromReader());
            assertEquals(csv, benchmark.jsonlFromPath());
            assertEquals(csv, benchmark.jsonlFromInputStream());
            assertTrue(csv > 0);
        } finally {
            benchmark.tearDown();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 10000})
    public void recordsMatchPojos(int size) {
        RecordProjectionJmhBenchmark benchmark = new RecordProjectionJmhBenchmark();
        benchmark.size = size;
        benchmark.setup();

        assertEquals(benchmark.pojoFilter(), benchmark.recordFilter());
        assertEquals(benchmark.pojoSelectProjection(), benchmark.recordSelectProjection());
        assertTrue(benchmark.recordFilter() > 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 10000})
    public void textFunctionPathsMatchCaseInsensitiveReference(int size) {
        TextFunctionJmhBenchmark benchmark = new TextFunctionJmhBenchmark();
        benchmark.size = size;
        benchmark.setup();
        long reference = TextFunctionJmhBenchmark.checksum(benchmark.rows().stream()
                .filter(row -> row.department.equalsIgnoreCase("engineering")).toList());

        assertEquals(reference, benchmark.sqlLikeLowerEquals());
        assertEquals(reference, benchmark.sqlLikeIlike());
        assertEquals(reference, benchmark.sqlLikeTextComputedField());
        assertTrue(reference > 0);
    }
}
