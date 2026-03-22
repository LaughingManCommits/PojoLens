package laughing.man.commits;

import laughing.man.commits.testing.FluentSqlLikeParity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class FluentSqlLikeParityTest {

    @Test
    public void orderedParityShouldPassForMatchingNormalizedRows() {
        FluentSqlLikeParity.assertOrderedEquals(
                List.of(new Row("a", 1), new Row("b", 2)),
                List.of(new Row("a", 1), new Row("b", 2)),
                row -> row.name + ":" + row.value
        );
    }

    @Test
    public void unorderedParityShouldIgnoreRowOrder() {
        FluentSqlLikeParity.assertUnorderedEquals(
                List.of(new Row("a", 1), new Row("b", 2)),
                List.of(new Row("b", 2), new Row("a", 1)),
                row -> row.name + ":" + row.value
        );
    }

    @Test
    public void orderedParityShouldFailForDifferentOrder() {
        assertThrows(AssertionError.class, () -> FluentSqlLikeParity.assertOrderedEquals(
                List.of(new Row("a", 1), new Row("b", 2)),
                List.of(new Row("b", 2), new Row("a", 1)),
                row -> row.name + ":" + row.value
        ));
    }

    public static class Row {
        public String name;
        public int value;

        public Row(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}

