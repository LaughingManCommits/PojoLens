package laughing.man.commits.builder;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterQueryBuilderSelectiveMaterializationTest {

    @Test
    void builderShouldMaterializeOnlyReferencedSourceFieldsForSimpleProjectionQueries() throws Exception {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos())
                .addRule("stringField", "a", Clauses.EQUAL, Separator.AND)
                .addField("integerField");

        List<QueryRow> rows = builder.getRows();

        assertEquals(List.of("stringField", "integerField"), fieldNames(rows.get(0)));
        assertEquals(2, builder.initFilter().filter(Foo.class).size());
    }

    @Test
    void builderShouldExpandMaterializedSourceFieldsWhenQueryShapeNeedsMoreColumns() throws Exception {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos())
                .addRule("stringField", "a", Clauses.EQUAL, Separator.AND)
                .addField("stringField");

        assertEquals(List.of("stringField"), fieldNames(builder.getRows().get(0)));

        builder.addOrder("integerField", 1);

        assertEquals(List.of("stringField", "integerField"), fieldNames(builder.getRows().get(0)));
        List<Foo> rows = builder.initFilter().filter(Sort.ASC, Foo.class);
        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0).getStringField());
    }

    @Test
    void joinQueriesShouldSelectOnlyNeededParentSourceFieldsBeforeJoinExecution() throws Exception {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleParents())
                .addJoinBeans("id", sampleChildren(), "parentId", Join.LEFT_JOIN)
                .addRule("tag", "c1", Clauses.EQUAL, Separator.AND)
                .addField("name")
                .addField("tag");

        assertEquals(List.of("id", "name"), fieldNames(builder.getRows().get(0)));

        List<JoinProjection> rows = builder.initFilter().join().filter(JoinProjection.class);
        assertEquals(1, rows.size());
        assertEquals("p1", rows.get(0).name);
        assertEquals("c1", rows.get(0).tag);
    }

    private static List<Foo> sampleFoos() {
        Date now = new Date();
        return Arrays.asList(
                new Foo("a", now, 2),
                new Foo("a", now, 1),
                new Foo("b", now, 3)
        );
    }

    private static List<Parent> sampleParents() {
        return List.of(new Parent(1, "p1"), new Parent(2, "p2"));
    }

    private static List<Child> sampleChildren() {
        return List.of(new Child(1, "c1"));
    }

    private static List<String> fieldNames(QueryRow row) {
        return row.getFields().stream()
                .map(QueryField::getFieldName)
                .toList();
    }

    static final class Parent {
        int id;
        String name;

        Parent(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final class Child {
        int parentId;
        String tag;

        Child(int parentId, String tag) {
            this.parentId = parentId;
            this.tag = tag;
        }
    }

    public static final class JoinProjection {
        public String name;
        public String tag;

        public JoinProjection() {
        }
    }
}
