package laughing.man.commits.util;

import laughing.man.commits.domain.QueryField;
import org.junit.jupiter.api.Test;

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

    private static QueryField queryField(String fieldName, Object value) {
        QueryField field = new QueryField();
        field.setFieldName(fieldName);
        field.setValue(value);
        return field;
    }
}
