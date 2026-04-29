package laughing.man.commits.facet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FacetPresetsTest {

    static class Row {
        public String region;
        public String status;

        Row(String region, String status) {
            this.region = region;
            this.status = status;
        }
    }

    @Test
    void distinctCountsReturnsSortedByCountDesc() {
        List<Row> rows = List.of(
                new Row("EMEA", "approved"),
                new Row("EMEA", "approved"),
                new Row("APAC", "declined"),
                new Row("EMEA", "declined"),
                new Row("APAC", "approved")
        );

        List<FacetOption> options = FacetPresets.distinctCounts("region").options(rows);

        assertEquals(2, options.size());
        assertEquals("EMEA", options.get(0).value());
        assertEquals(3L, options.get(0).count());
        assertEquals("APAC", options.get(1).value());
        assertEquals(2L, options.get(1).count());
    }

    @Test
    void distinctCountsHandlesSecondField() {
        List<Row> rows = List.of(
                new Row("EMEA", "approved"),
                new Row("APAC", "declined"),
                new Row("EMEA", "approved")
        );

        List<FacetOption> options = FacetPresets.distinctCounts("status").options(rows);

        assertEquals(2, options.size());
        assertEquals("approved", options.get(0).value());
        assertEquals(2L, options.get(0).count());
        assertEquals("declined", options.get(1).value());
        assertEquals(1L, options.get(1).count());
    }

    @Test
    void distinctCountsReturnsEmptyForNullOrEmptyInput() {
        assertTrue(FacetPresets.distinctCounts("region").options(null).isEmpty());
        assertTrue(FacetPresets.distinctCounts("region").options(List.of()).isEmpty());
    }

    @Test
    void distinctCountsRejectsBlankFieldName() {
        assertThrows(IllegalArgumentException.class, () -> FacetPresets.distinctCounts(""));
        assertThrows(IllegalArgumentException.class, () -> FacetPresets.distinctCounts(null));
    }

    @Test
    void distinctCountsSkipsNullRows() {
        List<Row> rows = new java.util.ArrayList<>();
        rows.add(new Row("EMEA", "approved"));
        rows.add(null);
        rows.add(new Row("EMEA", "declined"));

        List<FacetOption> options = FacetPresets.distinctCounts("region").options(rows);

        assertEquals(1, options.size());
        assertEquals("EMEA", options.get(0).value());
        assertEquals(2L, options.get(0).count());
    }
}
