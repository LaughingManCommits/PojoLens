package laughing.man.commits;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.internal.FluentEngine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for core value-comparison semantics shared by SQL-like,
 * natural, typed, and fluent execution paths.
 */
class CoreComparisonSemanticsTest {

    private static final TypedField<Row, String> TAG = TypedField.of("tag", String.class);
    private static final TypedField<Row, Status> STATUS = TypedField.of("status", Status.class);
    private static final TypedField<Row, Character> GRADE = TypedField.of("grade", Character.class);
    private static final TypedField<Row, Long> BIG = TypedField.of("big", Long.class);
    private static final TypedField<Row, Instant> AT = TypedField.of("at", Instant.class);

    @Test
    void enumFieldsCompareByConstantName() {
        List<Row> rows = List.of(row(1).status(Status.ACTIVE), row(2).status(Status.INACTIVE), row(3));

        assertEquals(List.of(1), ids(PojoLensSql.parse("where status = 'ACTIVE'").filter(rows, Row.class)));
        assertEquals(List.of(1), ids(PojoLensNatural.parse("show rows where status is ACTIVE").filter(rows, Row.class)));
        assertEquals(List.of(1), ids(TypedQuery.from(Row.class).where(STATUS.eq(Status.ACTIVE)).filter(rows)));
        assertEquals(List.of(1), ids(TypedQuery.from(Row.class).where(STATUS.in(Status.ACTIVE)).filter(rows)));
        assertEquals(List.of(2), ids(TypedQuery.from(Row.class).where(STATUS.ne(Status.ACTIVE)).filter(rows)));
    }

    @Test
    void enumOrderingFollowsDeclarationOrder() {
        List<Row> rows = List.of(row(1).status(Status.ACTIVE), row(2).status(Status.INACTIVE), row(3).status(Status.ARCHIVED));

        assertEquals(List.of(2, 3), ids(PojoLensSql.parse("where status > 'ACTIVE'").filter(rows, Row.class)));
        assertEquals(List.of(1, 2), ids(TypedQuery.from(Row.class).where(STATUS.lte(Status.INACTIVE)).filter(rows)));
    }

    @Test
    void characterFieldsCompareAsSingleCharacterText() {
        List<Row> rows = List.of(row(1).grade('A'), row(2).grade('B'));

        assertEquals(List.of(1), ids(PojoLensSql.parse("where grade = 'A'").filter(rows, Row.class)));
        assertEquals(List.of(1), ids(TypedQuery.from(Row.class).where(GRADE.eq('A')).filter(rows)));
    }

    @Test
    void notEqualNeverMatchesNullFieldOnAnyPath() {
        List<Row> rows = List.of(row(1), row(2).tag("x"), row(3).tag("y"));

        assertEquals(List.of(3), ids(PojoLensSql.parse("where tag != 'x'").filter(rows, Row.class)));
        assertEquals(List.of(3), ids(PojoLensSql.parse("where tag != 'x' and id > 0").filter(rows, Row.class)));
        assertEquals(List.of(3), ids(PojoLensSql.parse("where tag != 'x' or id > 99").filter(rows, Row.class)));
        assertEquals(List.of(3), ids(PojoLensNatural.parse("show rows where tag is not x").filter(rows, Row.class)));
        assertEquals(List.of(3), ids(TypedQuery.from(Row.class).where(TAG.ne("x")).filter(rows)));
        assertEquals(List.of(3), ids(TypedQuery.from(Row.class).where(TAG.eq("x").not()).filter(rows)));
        assertEquals(List.of(2, 3), ids(PojoLensSql.parse("where tag != null").filter(rows, Row.class)));
    }

    @Test
    void integralComparisonsKeepFullLongPrecision() {
        List<Row> rows = List.of(row(1).big(9_007_199_254_740_993L), row(2).big(9_007_199_254_740_992L), row(3).big(1L));

        assertEquals(List.of(2), ids(PojoLensSql.parse("where big = 9007199254740992").filter(rows, Row.class)));
        assertEquals(List.of(1), ids(PojoLensSql.parse("where big > 9007199254740992").filter(rows, Row.class)));
        assertEquals(List.of(1, 3), ids(PojoLensSql.parse("where big != 9007199254740992").filter(rows, Row.class)));
        assertEquals(List.of(1), ids(PojoLensSql.parse("where big = :p")
                .params(Map.of("p", "9007199254740993")).filter(rows, Row.class)));
        assertEquals(List.of(1), ids(TypedQuery.from(Row.class).where(BIG.eq(9_007_199_254_740_993L)).filter(rows)));
    }

    @Test
    void isoDateLiteralsCompareAgainstTemporalFields() {
        List<Row> rows = List.of(
                row(1).day(LocalDate.of(2024, 1, 1)).at(Instant.parse("2023-12-31T12:00:00Z")),
                row(2).day(LocalDate.of(2024, 1, 2)).at(Instant.parse("2024-01-02T12:00:00Z")),
                row(3).day(LocalDate.of(2024, 1, 3)).at(Instant.parse("2024-01-03T12:00:00Z"))
        );

        assertEquals(List.of(3), ids(PojoLensSql.parse("where day > '2024-01-02'").filter(rows, Row.class)));
        assertEquals(List.of(3), ids(PojoLensNatural.parse("show rows where day is after 2024-01-02").filter(rows, Row.class)));
        assertEquals(List.of(2, 3), ids(PojoLensSql.parse("where at > '2024-01-01T00:00:00Z'").filter(rows, Row.class)));
    }

    @Test
    void stringFieldsSupportOrderingOperators() {
        List<Row> rows = List.of(row(1).tag("apple"), row(2).tag("x"), row(3).tag("y"), row(4).tag("10"));

        assertEquals(List.of(2, 3), ids(PojoLensSql.parse("where tag >= 'x'").filter(rows, Row.class)));
        assertEquals(List.of(1, 2), ids(TypedQuery.from(Row.class).where(TAG.between("a", "x")).filter(rows)));
        assertEquals(List.of(4), ids(PojoLensSql.parse("where tag > 9").filter(rows, Row.class)));
    }

    @Test
    void nullInsideInListNeverMatchesTheWordNull() {
        List<Row> rows = List.of(row(1).tag("null"), row(2), row(3).tag("x"));

        assertEquals(List.of(3), ids(TypedQuery.from(Row.class).where(TAG.in(Arrays.asList("x", null))).filter(rows)));
    }

    @Test
    void numericTextParsingIgnoresDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            List<Row> rows = List.of(row(1), row(2));

            assertEquals(List.of(1), ids(PojoLensSql.parse("where id = '1.0'").filter(rows, Row.class)));
            assertEquals(List.of(), ids(PojoLensSql.parse("where id = '1,0'").filter(rows, Row.class)));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void instantsInDaylightSavingOverlapStayDistinct() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            // 01:30 local twice on 2024-11-03: once in EDT, once in EST.
            List<Row> rows = List.of(
                    row(1).at(Instant.parse("2024-11-03T05:30:00Z")),
                    row(2).at(Instant.parse("2024-11-03T06:30:00Z"))
            );

            assertEquals(List.of(2), ids(TypedQuery.from(Row.class)
                    .where(AT.gt(Instant.parse("2024-11-03T05:30:00Z"))).filter(rows)));
            assertEquals(List.of(1), ids(TypedQuery.from(Row.class)
                    .where(AT.eq(Instant.parse("2024-11-03T05:30:00Z"))).filter(rows)));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void equalityIndexHintNeverChangesResults() {
        List<Foo> source = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            source.add(new Foo("group-" + (i % 5), new Date(1_700_000_000_000L), i % 3));
        }

        assertEquals(
                fooIds(FluentEngine.newQueryBuilder(source)
                        .addRule("integerField", 1L, Clauses.EQUAL, Separator.AND)
                        .initFilter().filter(Foo.class)),
                fooIds(FluentEngine.newQueryBuilder(source)
                        .addIndex("integerField")
                        .addRule("integerField", 1L, Clauses.EQUAL, Separator.AND)
                        .initFilter().filter(Foo.class))
        );
        assertEquals(
                fooIds(FluentEngine.newQueryBuilder(source)
                        .addRule("stringField", "group-1", Clauses.EQUAL, Separator.AND)
                        .addRule("integerField", 2, Clauses.EQUAL, Separator.OR)
                        .initFilter().filter(Foo.class)),
                fooIds(FluentEngine.newQueryBuilder(source)
                        .addIndex("stringField")
                        .addRule("stringField", "group-1", Clauses.EQUAL, Separator.AND)
                        .addRule("integerField", 2, Clauses.EQUAL, Separator.OR)
                        .initFilter().filter(Foo.class))
        );
    }

    private static List<Integer> ids(List<Row> rows) {
        return rows.stream().map(row -> row.id).toList();
    }

    private static List<String> fooIds(List<Foo> rows) {
        return rows.stream().map(foo -> foo.getStringField() + ":" + foo.getIntegerField()).toList();
    }

    private static Row row(int id) {
        Row row = new Row();
        row.id = id;
        return row;
    }

    public enum Status {
        ACTIVE,
        INACTIVE,
        ARCHIVED
    }

    public static class Row {
        public int id;
        public String tag;
        public Status status;
        public Character grade;
        public long big;
        public LocalDate day;
        public Instant at;

        Row() {
        }

        Row tag(String value) {
            tag = value;
            return this;
        }

        Row status(Status value) {
            status = value;
            return this;
        }

        Row grade(char value) {
            grade = value;
            return this;
        }

        Row big(long value) {
            big = value;
            return this;
        }

        Row day(LocalDate value) {
            day = value;
            return this;
        }

        Row at(Instant value) {
            at = value;
            return this;
        }
    }
}
