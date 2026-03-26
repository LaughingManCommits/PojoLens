package laughing.man.commits;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Sort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PojoLensOrderGroupBehaviorTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("laughing.man.commits.PojoLensBehaviorFixtures#sortCases")
    public void sortOperatorsShouldBothBeExercised(String caseName,
                                                   Sort sort,
                                                   int expectedFirst,
                                                   int expectedLast) throws Exception {
        Date now = new Date();
        List<Foo> source = Arrays.asList(
                new Foo("a", now, 1),
                new Foo("b", now, 3),
                new Foo("c", now, 2)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addOrder("integerField", 1)
                .initFilter()
                .filter(sort, Foo.class);
        assertEquals(expectedFirst, results.get(0).getIntegerField());
        assertEquals(expectedLast, results.get(2).getIntegerField());
    }

    @Test
    public void addOrderWithoutIndexShouldUseInsertionPriority() throws Exception {
        Date now = new Date();
        List<Foo> source = Arrays.asList(
                new Foo("b", now, 1),
                new Foo("a", now, 2),
                new Foo("a", now, 1)
        );

        List<Foo> stringThenInteger = PojoLensCore.newQueryBuilder(source)
                .addOrder("stringField")
                .addOrder("integerField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);
        assertEquals("a", stringThenInteger.get(0).getStringField());
        assertEquals(1, stringThenInteger.get(0).getIntegerField());
        assertEquals("a", stringThenInteger.get(1).getStringField());
        assertEquals(2, stringThenInteger.get(1).getIntegerField());
        assertEquals("b", stringThenInteger.get(2).getStringField());

        List<Foo> integerThenString = PojoLensCore.newQueryBuilder(source)
                .addOrder("integerField")
                .addOrder("stringField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);
        assertEquals(1, integerThenString.get(0).getIntegerField());
        assertEquals("a", integerThenString.get(0).getStringField());
        assertEquals(1, integerThenString.get(1).getIntegerField());
        assertEquals("b", integerThenString.get(1).getStringField());
    }

    @Test
    public void distinctThenSortShouldBeDeterministic() throws Exception {
        Date now = new Date();
        List<Foo> source = Arrays.asList(
                new Foo("a", now, 5),
                new Foo("a", now, 1),
                new Foo("b", now, 4),
                new Foo("b", now, 2)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addDistinct("stringField", 1)
                .addOrder("integerField", 1)
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).getIntegerField());
        assertEquals(2, results.get(1).getIntegerField());
    }

    @Test
    public void groupingOnMultipleFieldsShouldReturnCompositeKeys() throws Exception {
        Date now = new Date();
        List<Foo> source = Arrays.asList(
                new Foo("a", now, 1),
                new Foo("a", now, 2),
                new Foo("b", now, 1)
        );

        Map<String, List<Foo>> grouped = PojoLensCore.newQueryBuilder(source)
                .addGroup("stringField", 1)
                .addGroup("integerField", 2)
                .initFilter()
                .filterGroups(Foo.class);

        assertEquals(3, grouped.size());
    }

    @Test
    public void addGroupWithoutIndexShouldUseInsertionPriority() throws Exception {
        Date now = new Date();
        List<Foo> source = Arrays.asList(
                new Foo("a", now, 1),
                new Foo("a", now, 2),
                new Foo("b", now, 1)
        );

        Map<String, List<Foo>> groupedStringThenInteger = PojoLensCore.newQueryBuilder(source)
                .addGroup("stringField")
                .addGroup("integerField")
                .initFilter()
                .filterGroups(Foo.class);
        assertTrue(groupedStringThenInteger.containsKey("a,1,"));
        assertTrue(groupedStringThenInteger.containsKey("a,2,"));
        assertTrue(groupedStringThenInteger.containsKey("b,1,"));

        Map<String, List<Foo>> groupedIntegerThenString = PojoLensCore.newQueryBuilder(source)
                .addGroup("integerField")
                .addGroup("stringField")
                .initFilter()
                .filterGroups(Foo.class);
        assertTrue(groupedIntegerThenString.containsKey("1,a,"));
        assertTrue(groupedIntegerThenString.containsKey("2,a,"));
        assertTrue(groupedIntegerThenString.containsKey("1,b,"));
    }
}

