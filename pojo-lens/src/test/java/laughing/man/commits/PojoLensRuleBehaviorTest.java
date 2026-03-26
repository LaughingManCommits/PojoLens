package laughing.man.commits;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Separator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static laughing.man.commits.builder.QueryRule.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PojoLensRuleBehaviorTest {

    @Test
    public void emptyListShouldReturnEmptyResults() throws Exception {
        List<Foo> results = PojoLensCore.newQueryBuilder(Collections.<Foo>emptyList())
                .initFilter()
                .filter(Foo.class);
        assertTrue(results.isEmpty());
    }

    @Test
    public void invalidRuleFieldShouldBeIgnored() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("a", new Date(), 1),
                new Foo("b", new Date(), 2)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addRule("doesNotExist", "x", Clauses.EQUAL, Separator.OR)
                .initFilter()
                .filter(Foo.class);

        assertEquals(2, results.size());
    }

    @Test
    public void booleanFalseComparisonShouldWork() throws Exception {
        List<PojoLensBehaviorFixtures.BoolBean> source = Arrays.asList(
                new PojoLensBehaviorFixtures.BoolBean("on", true),
                new PojoLensBehaviorFixtures.BoolBean("off", false)
        );

        List<PojoLensBehaviorFixtures.BoolBean> results = PojoLensCore.newQueryBuilder(source)
                .addRule("active", false, Clauses.EQUAL, Separator.OR)
                .initFilter()
                .filter(PojoLensBehaviorFixtures.BoolBean.class);

        assertEquals(1, results.size());
        assertEquals("off", results.get(0).name);
        assertFalse(results.get(0).active);
    }

    @Test
    public void addRuleWithThreeArgsShouldDefaultToAnd() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 1),
                new Foo("x", new Date(), 2),
                new Foo("y", new Date(), 1)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "x", Clauses.EQUAL)
                .addRule("integerField", 1, Clauses.EQUAL)
                .initFilter()
                .filter(Foo.class);

        assertEquals(1, results.size());
        assertEquals("x", results.get(0).getStringField());
        assertEquals(1, results.get(0).getIntegerField());
    }

    @Test
    public void typedSelectorApiShouldResolveFieldNames() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("a", new Date(), 1),
                new Foo("b", new Date(), 2),
                new Foo("a", new Date(), 3)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addRule(Foo::getStringField, "a", Clauses.EQUAL)
                .addOrder(Foo::getIntegerField, 1)
                .addDistinct(Foo::getStringField, 1)
                .addField(Foo::getStringField)
                .addField(Foo::getIntegerField)
                .initFilter()
                .filter(Foo.class);

        assertEquals(1, results.size());
        assertEquals("a", results.get(0).getStringField());
    }

    @Test
    public void groupedRuleApiShouldEvaluatePredictably() {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 5),
                new Foo("x", new Date(), 20),
                new Foo("y", new Date(), 20)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .allOf(
                        of(Foo::getStringField, "x", Clauses.EQUAL),
                        of(Foo::getIntegerField, 10, Clauses.BIGGER_EQUAL)
                )
                .anyOf(
                        of(Foo::getIntegerField, 20, Clauses.EQUAL),
                        of(Foo::getIntegerField, 30, Clauses.EQUAL)
                )
                .initFilter()
                .filter(Foo.class);

        assertEquals(1, results.size());
        assertEquals("x", results.get(0).getStringField());
        assertEquals(20, results.get(0).getIntegerField());
    }

    @Test
    public void groupedRuleApiShouldIgnoreEmptyGroups() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 1),
                new Foo("y", new Date(), 2)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .allOf()
                .anyOf()
                .initFilter()
                .filter(Foo.class);

        assertEquals(2, results.size());
    }

    @Test
    public void explicitGroupsShouldTakePrecedenceOverLegacyRuleList() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 1),
                new Foo("y", new Date(), 2)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "y", Clauses.EQUAL, Separator.AND)
                .allOf(of(Foo::getStringField, "x", Clauses.EQUAL))
                .initFilter()
                .filter(Foo.class);

        assertEquals(1, results.size());
        assertEquals("x", results.get(0).getStringField());
    }

    @Test
    public void groupedRuleApiShouldUseRealNullSemantics() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 1),
                new Foo(null, new Date(), 2),
                new Foo("null", new Date(), 3)
        );

        List<Foo> equalNullResults = PojoLensCore.newQueryBuilder(source)
                .allOf(of(Foo::getStringField, null, Clauses.EQUAL))
                .initFilter()
                .filter(Foo.class);

        assertEquals(1, equalNullResults.size());
        assertNull(equalNullResults.get(0).getStringField());

        List<Foo> notEqualNullResults = PojoLensCore.newQueryBuilder(source)
                .allOf(of(Foo::getStringField, null, Clauses.NOT_EQUAL))
                .initFilter()
                .filter(Foo.class);

        assertEquals(2, notEqualNullResults.size());
        List<String> values = notEqualNullResults.stream()
                .map(Foo::getStringField)
                .toList();
        assertTrue(values.contains("null"));
        assertTrue(values.contains("x"));
    }

    @Test
    public void groupedRuleApiShouldOrAcrossMultipleAllOfGroups() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 1),
                new Foo("y", new Date(), 2),
                new Foo("x", new Date(), 9)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .allOf(
                        of(Foo::getStringField, "x", Clauses.EQUAL),
                        of(Foo::getIntegerField, 1, Clauses.EQUAL)
                )
                .allOf(
                        of(Foo::getStringField, "y", Clauses.EQUAL),
                        of(Foo::getIntegerField, 2, Clauses.EQUAL)
                )
                .initFilter()
                .filter(Foo.class);

        assertEquals(2, results.size());
    }

    @Test
    public void groupedRuleApiShouldOrAcrossMultipleAnyOfGroups() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 1),
                new Foo("y", new Date(), 2),
                new Foo("z", new Date(), 3)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .anyOf(of(Foo::getStringField, "x", Clauses.EQUAL))
                .anyOf(of(Foo::getIntegerField, 3, Clauses.EQUAL))
                .initFilter()
                .filter(Foo.class);

        assertEquals(2, results.size());
    }

    @Test
    public void groupedRuleApiShouldSupportMixedTypedAndStringRules() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 10),
                new Foo("x", new Date(), 20),
                new Foo("y", new Date(), 20)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .allOf(
                        of(Foo::getStringField, "x", Clauses.EQUAL),
                        of("integerField", 20, Clauses.EQUAL)
                )
                .initFilter()
                .filter(Foo.class);

        assertEquals(1, results.size());
        assertEquals("x", results.get(0).getStringField());
        assertEquals(20, results.get(0).getIntegerField());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("laughing.man.commits.PojoLensBehaviorFixtures#numericClauseCases")
    public void numericClauseOperatorsShouldReturnExpectedRow(String caseName,
                                                              Clauses clause,
                                                              int compareValue,
                                                              int expectedInteger) throws Exception {
        List<Foo> source = PojoLensBehaviorFixtures.numericClauseSource();
        PojoLensBehaviorFixtures.assertSingleIntegerResult(source, clause, compareValue, expectedInteger);
    }

    @Test
    public void clausesOperatorsShouldHandleNonNumericOperators() throws Exception {
        List<Foo> source = PojoLensBehaviorFixtures.numericClauseSource();

        List<Foo> notEqualResults = PojoLensCore.newQueryBuilder(source)
                .addRule("integerField", 20, Clauses.NOT_EQUAL, Separator.OR)
                .initFilter()
                .filter(Foo.class);
        assertEquals(2, notEqualResults.size());

        List<Foo> containsResults = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "bc", Clauses.CONTAINS, Separator.OR)
                .initFilter()
                .filter(Foo.class);
        assertEquals(2, containsResults.size());

        List<Foo> matchesResults = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "^[0-9]+$", Clauses.MATCHES, Separator.OR)
                .initFilter()
                .filter(Foo.class);
        assertEquals(1, matchesResults.size());
        assertEquals("123", matchesResults.get(0).getStringField());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("laughing.man.commits.PojoLensBehaviorFixtures#separatorCases")
    public void separatorOperatorsShouldBothBeExercised(String caseName,
                                                        Separator separator,
                                                        int expectedSize) throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("x", new Date(), 1),
                new Foo("x", new Date(), 2),
                new Foo("y", new Date(), 1)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "x", Clauses.EQUAL, separator)
                .addRule("integerField", 1, Clauses.EQUAL, separator)
                .initFilter()
                .filter(Foo.class);
        assertEquals(expectedSize, results.size());
    }

    @Test
    public void dateComparisonShouldHonor24HourDefault() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.JANUARY, 1, 1, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date oneAm = cal.getTime();

        cal.set(2024, Calendar.JANUARY, 1, 13, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date onePm = cal.getTime();

        List<Foo> source = Arrays.asList(
                new Foo("am", oneAm, 1),
                new Foo("pm", onePm, 2)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addRule("dateField", oneAm, Clauses.BIGGER, Separator.OR)
                .initFilter()
                .filter(Foo.class);

        assertEquals(1, results.size());
        assertEquals("pm", results.get(0).getStringField());
    }

    @Test
    public void matchesClauseShouldFilterRegexPattern() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("123", new Date(), 1),
                new Foo("abc", new Date(), 2),
                new Foo("x123y", new Date(), 3)
        );

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", "^[0-9]+$", Clauses.MATCHES, Separator.OR)
                .initFilter()
                .filter(Foo.class);

        assertEquals(1, results.size());
        assertEquals("123", results.get(0).getStringField());
    }

    @Test
    public void arrayAndListRuleValuesShouldBeSupported() throws Exception {
        List<Foo> source = Arrays.asList(
                new Foo("a", new Date(), 1),
                new Foo("b", new Date(), 2),
                new Foo("c", new Date(), 3)
        );

        String[] arrayValues = new String[]{"a", "x"};
        List<String> listValues = Arrays.asList("b", "y");

        List<Foo> results = PojoLensCore.newQueryBuilder(source)
                .addRule("stringField", arrayValues, Clauses.EQUAL, Separator.OR)
                .addRule("stringField", listValues, Clauses.EQUAL, Separator.OR)
                .initFilter()
                .filter(Foo.class);

        assertEquals(2, results.size());
    }

    @Test
    public void groupedRuleApiShouldTreatUnmatchedLeftJoinChildFieldsAsRealNull() throws Exception {
        List<PojoLensBehaviorFixtures.ParentBean> parents = Arrays.asList(
                new PojoLensBehaviorFixtures.ParentBean(1, "p1"),
                new PojoLensBehaviorFixtures.ParentBean(2, "p2")
        );
        List<PojoLensBehaviorFixtures.ChildBean> children = Collections.singletonList(
                new PojoLensBehaviorFixtures.ChildBean(1, "c1")
        );

        List<PojoLensBehaviorFixtures.ParentBean> results = PojoLensCore.newQueryBuilder(parents)
                .addJoinBeans("id", children, "parentId", Join.LEFT_JOIN)
                .allOf(of("tag", null, Clauses.EQUAL))
                .initFilter()
                .join()
                .filter(PojoLensBehaviorFixtures.ParentBean.class);

        assertEquals(1, results.size());
        assertEquals(2, results.get(0).id);
    }
}

