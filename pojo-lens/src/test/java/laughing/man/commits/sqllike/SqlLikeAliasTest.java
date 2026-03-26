package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensSql;

import laughing.man.commits.PojoLens;
import laughing.man.commits.domain.Foo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SqlLikeAliasTest {

    @Test
    public void selectAliasesShouldProjectToAliasFields() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1),
                new Foo("abc", new Date(), 3),
                new Foo("xyz", new Date(), 9)
        );

        List<FooAliasProjection> rows = PojoLensSql.parse("select stringField as label, integerField as amount where stringField = 'abc' order by integerField asc")
                .filter(source, FooAliasProjection.class);

        assertEquals(2, rows.size());
        assertEquals("abc", rows.get(0).label);
        assertEquals(1, rows.get(0).amount);
        assertEquals("abc", rows.get(1).label);
        assertEquals(3, rows.get(1).amount);
    }

    @Test
    public void duplicateAliasOutputNamesShouldBeRejected() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1)
        );

        try {
            PojoLensSql.parse("select stringField as value, integerField as value where integerField >= 1")
                    .filter(source, DuplicateAliasProjection.class);
            fail("Expected duplicate alias failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Duplicate SELECT output name 'value'"));
        }
    }

    @Test
    public void aliasCollisionWithFieldOutputNameShouldBeRejected() {
        List<Foo> source = Arrays.asList(
                new Foo("abc", new Date(), 1)
        );

        try {
            PojoLensSql.parse("select stringField as integerField, integerField where integerField >= 1")
                    .filter(source, Foo.class);
            fail("Expected output collision failure");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Duplicate SELECT output name 'integerField'"));
        }
    }

    public static class FooAliasProjection {
        String label;
        int amount;

        public FooAliasProjection() {
        }
    }

    public static class DuplicateAliasProjection {
        String value;

        public DuplicateAliasProjection() {
        }
    }
}





