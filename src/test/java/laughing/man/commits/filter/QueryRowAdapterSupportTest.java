package laughing.man.commits.filter;

import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRowAdapterSupportTest {

    @Test
    void toQueryRowsShouldMaterializeSchemaOrderedFieldsFromArrayRows() {
        List<QueryRow> rows = QueryRowAdapterSupport.toQueryRows(
                List.of("alpha", "beta"),
                List.of(new Object[]{1, "x"}, new Object[]{2})
        );

        assertEquals(2, rows.size());
        assertEquals(List.of("alpha", "beta"), rows.get(0).getFields().stream().map(QueryField::getFieldName).toList());
        assertEquals(List.of(1, "x"), rows.get(0).getFields().stream().map(QueryField::getValue).toList());
        assertEquals(Arrays.asList(2, null), rows.get(1).getFields().stream().map(QueryField::getValue).toList());
    }

    @Test
    void toQueryRowsShouldReturnEmptyListForMissingRows() {
        assertTrue(QueryRowAdapterSupport.toQueryRows(List.of("alpha"), List.of()).isEmpty());
        assertTrue(QueryRowAdapterSupport.toQueryRows(List.of("alpha"), null).isEmpty());
    }
}
