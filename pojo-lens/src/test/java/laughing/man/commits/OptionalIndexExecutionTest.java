package laughing.man.commits;

import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptionalIndexExecutionTest {

    @Test
    public void indexedEqualityHintShouldNotChangeFilterResults() {
        List<Foo> source = sampleRows(2_000);

        List<Foo> baseline = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "group-3", Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 500, Clauses.BIGGER_EQUAL, Separator.AND)
                .initFilter()
                .filter(Foo.class);

        List<Foo> indexed = PojoLensCore.newQueryBuilder(source)
                .addIndex("stringField")
                .addRule("stringField", "group-3", Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 500, Clauses.BIGGER_EQUAL, Separator.AND)
                .initFilter()
                .filter(Foo.class);

        assertEquals(signatures(baseline), signatures(indexed));
    }

    @Test
    public void missingIndexedFieldShouldFallbackToNormalScan() {
        List<Foo> source = sampleRows(500);

        List<Foo> baseline = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "group-1", Clauses.EQUAL, Separator.AND)
                .initFilter()
                .filter(Foo.class);

        List<Foo> indexed = PojoLensCore.newQueryBuilder(source)
                .addIndex("missingField")
                .addRule("stringField", "group-1", Clauses.EQUAL, Separator.AND)
                .initFilter()
                .filter(Foo.class);

        assertEquals(signatures(baseline), signatures(indexed));
    }

    @Test
    public void explainShouldExposeConfiguredIndexFields() {
        QueryBuilder builder = PojoLensCore.newQueryBuilder(sampleRows(10))
                .addIndex("stringField");

        Map<String, Object> explain = builder.explain();
        Object indexes = explain.get("indexes");
        assertTrue(indexes instanceof List<?>);
        assertEquals(List.of("stringField"), indexes);
    }

    private static List<Foo> sampleRows(int size) {
        List<Foo> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new Foo(
                    "group-" + (i % 7),
                    new Date(1_700_000_000_000L + (i * 60_000L)),
                    i
            ));
        }
        return rows;
    }

    private static List<String> signatures(List<Foo> rows) {
        List<String> values = new ArrayList<>(rows.size());
        for (Foo row : rows) {
            values.add(row.getStringField() + ":" + row.getIntegerField());
        }
        return values;
    }
}
