package laughing.man.commits.util;

import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaIndexUtilTest {

    @Test
    void indexFieldNamesShouldKeepFirstIndexAndSkipBlankNames() {
        Map<String, Integer> indexes = SchemaIndexUtil.indexFieldNames(
                List.of("alpha", "", "beta", "alpha")
        );

        assertEquals(0, indexes.get("alpha"));
        assertEquals(2, indexes.get("beta"));
        assertEquals(-1, SchemaIndexUtil.findFieldIndex(indexes, "missing"));
    }

    @Test
    void indexQueryFieldsShouldKeepFirstIndexAndSkipMissingNames() {
        Map<String, Integer> indexes = SchemaIndexUtil.indexQueryFields(List.of(
                queryField("alpha", 1),
                queryField(null, 2),
                queryField("beta", 3),
                queryField("alpha", 4)
        ));

        assertEquals(0, indexes.get("alpha"));
        assertEquals(2, indexes.get("beta"));
    }

    @Test
    void queryFieldNamesShouldPreserveSchemaOrderAndBlankMissingNames() {
        List<String> names = SchemaIndexUtil.queryFieldNames(Arrays.asList(
                queryField("alpha", 1),
                queryField(null, 2),
                null,
                queryField("beta", 3)
        ));

        assertEquals(List.of("alpha", "", "", "beta"), names);
        assertEquals(List.of(), SchemaIndexUtil.queryFieldNames(null));
    }

    @Test
    void firstQueryRowFieldNamesShouldSkipLeadingEmptyRows() {
        QueryRow empty = new QueryRow();
        empty.setFields(List.of());
        QueryRow row = new QueryRow();
        row.setFields(List.of(queryField("alpha", 1), queryField(null, 2), queryField("beta", 3)));

        List<String> names = SchemaIndexUtil.firstQueryRowFieldNames(Arrays.asList(null, empty, row));

        assertEquals(List.of("alpha", "", "beta"), names);
        assertEquals(List.of(), SchemaIndexUtil.firstQueryRowFieldNames(List.of()));
    }

    private static QueryField queryField(String fieldName, Object value) {
        QueryField field = new QueryField();
        field.setFieldName(fieldName);
        field.setValue(value);
        return field;
    }
}


