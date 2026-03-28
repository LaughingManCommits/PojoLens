package laughing.man.commits.util;

import laughing.man.commits.domain.QueryField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFieldLookupUtilTest {

    @Test
    void findFieldIndexShouldSkipNullEntriesAndReturnFirstExactMatch() {
        List<QueryField> fields = Arrays.asList(
                queryField("alpha", 1),
                null,
                queryField("beta", 2),
                queryField("alpha", 3)
        );

        assertEquals(0, QueryFieldLookupUtil.findFieldIndex(fields, "alpha"));
        assertEquals(2, QueryFieldLookupUtil.findFieldIndex(fields, "beta"));
        assertEquals(-1, QueryFieldLookupUtil.findFieldIndex(fields, "missing"));
        assertTrue(QueryFieldLookupUtil.containsField(fields, "beta"));
        assertFalse(QueryFieldLookupUtil.containsField(fields, "missing"));
    }

    @Test
    void findFieldValueShouldUsePreferredIndexAndFallbackToScan() {
        List<QueryField> fields = List.of(
                queryField("alpha", 1),
                queryField("beta", 2),
                queryField("gamma", 3)
        );

        assertEquals(2, QueryFieldLookupUtil.findFieldValue(fields, "beta", 1));
        assertEquals(3, QueryFieldLookupUtil.findFieldValue(fields, "gamma", 0));
        assertNull(QueryFieldLookupUtil.findFieldValue(fields, "missing", 2));
        assertNull(QueryFieldLookupUtil.findFieldValue(null, "alpha"));
    }

    private static QueryField queryField(String fieldName, Object value) {
        QueryField field = new QueryField();
        field.setFieldName(fieldName);
        field.setValue(value);
        return field;
    }
}


