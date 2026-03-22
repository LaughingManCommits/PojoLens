package laughing.man.commits.filter;

import laughing.man.commits.PojoLens;
import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.domain.Foo;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.util.ReflectionUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastPojoFilterSupportTest {

    private static List<Foo> sampleFoos() {
        return List.of(
                new Foo("alpha", new Date(), 10),
                new Foo("beta", new Date(), 20),
                new Foo("alpha", new Date(), 30),
                new Foo("gamma", new Date(), 40)
        );
    }

    @Test
    void exactMatchFilterShouldReturnOnlyMatchingRows() throws Exception {
        List<Foo> result = PojoLens.newQueryBuilder(sampleFoos())
                .addRule("stringField", "alpha", Clauses.EQUAL, Separator.AND)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(f -> "alpha".equals(f.getStringField())));
    }

    @Test
    void numericFilterShouldReturnRowsAboveThreshold() throws Exception {
        List<Foo> result = PojoLens.newQueryBuilder(sampleFoos())
                .addRule("integerField", 25, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(f -> f.getIntegerField() >= 25));
    }

    @Test
    void andRulesShouldRequireBothConditions() throws Exception {
        List<Foo> result = PojoLens.newQueryBuilder(sampleFoos())
                .addRule("stringField", "alpha", Clauses.EQUAL, Separator.AND)
                .addRule("integerField", 20, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        // Only Foo("alpha", ..., 30) matches both rules
        assertEquals(1, result.size());
        assertEquals("alpha", result.get(0).getStringField());
        assertEquals(30, result.get(0).getIntegerField());
    }

    @Test
    void orRuleShouldIncludeEitherMatch() throws Exception {
        List<Foo> result = PojoLens.newQueryBuilder(sampleFoos())
                .addRule("stringField", "alpha", Clauses.EQUAL, Separator.OR)
                .addRule("stringField", "beta", Clauses.EQUAL, Separator.OR)
                .addField("stringField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(f ->
                "alpha".equals(f.getStringField()) || "beta".equals(f.getStringField())));
    }

    @Test
    void noMatchShouldReturnEmptyList() throws Exception {
        List<Foo> result = PojoLens.newQueryBuilder(sampleFoos())
                .addRule("stringField", "delta", Clauses.EQUAL, Separator.AND)
                .addField("stringField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        assertTrue(result.isEmpty());
    }

    @Test
    void orderByShouldSortFilteredRows() throws Exception {
        List<Foo> result = PojoLens.newQueryBuilder(sampleFoos())
                .addRule("stringField", "alpha", Clauses.EQUAL, Separator.AND)
                .addOrder("integerField", 1)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        assertEquals(2, result.size());
        assertTrue(result.get(0).getIntegerField() <= result.get(1).getIntegerField());
    }

    @Test
    void distinctShouldDeduplicateAfterFilter() throws Exception {
        List<Foo> result = PojoLens.newQueryBuilder(sampleFoos())
                .addRule("stringField", "alpha", Clauses.EQUAL, Separator.AND)
                .addDistinct("integerField", 1)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        // Both alpha rows have distinct integerField values (10, 30), so both appear
        assertEquals(2, result.size());
    }

    @Test
    void fastPathResultMatchesStandardPathResult() throws Exception {
        List<Foo> source = sampleFoos();
        String rule = "alpha";

        // Fast path: POJO source triggers FastPojoFilterSupport
        List<Foo> fastResult = PojoLens.newQueryBuilder(source)
                .addRule("stringField", rule, Clauses.EQUAL, Separator.AND)
                .addOrder("integerField", 1)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        // Standard path: QueryRow source bypasses fast path
        List<QueryRow> rows = ReflectionUtil.toDomainRows(source);
        List<Foo> standardResult = PojoLens.newQueryBuilder(rows)
                .addRule("stringField", rule, Clauses.EQUAL, Separator.AND)
                .addOrder("integerField", 1)
                .addField("stringField")
                .addField("integerField")
                .initFilter()
                .filter(Sort.ASC, Foo.class);

        assertEquals(standardResult.size(), fastResult.size());
        for (int i = 0; i < standardResult.size(); i++) {
            assertEquals(standardResult.get(i).getStringField(), fastResult.get(i).getStringField());
            assertEquals(standardResult.get(i).getIntegerField(), fastResult.get(i).getIntegerField());
        }
    }

    @Test
    void isApplicableShouldReturnTrueForSimplePojoFilter() {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos());
        builder.addRule("stringField", "alpha", Clauses.EQUAL, Separator.AND);

        assertTrue(FastPojoFilterSupport.isApplicable(builder));
    }

    @Test
    void isApplicableShouldReturnFalseForQueryRowSource() {
        List<QueryRow> rows = ReflectionUtil.toDomainRows(sampleFoos());
        FilterQueryBuilder builder = new FilterQueryBuilder(rows);
        builder.addRule("stringField", "alpha", Clauses.EQUAL, Separator.AND);

        assertFalse(FastPojoFilterSupport.isApplicable(builder));
    }

    @Test
    void isApplicableShouldReturnFalseWhenNoFilterRules() {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos());

        assertFalse(FastPojoFilterSupport.isApplicable(builder));
    }

    @Test
    void tryFilterRowsShouldReadOnlyRequiredFieldsWhenProjectionIsExplicit() {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos());
        builder.addRule("stringField", "alpha", Clauses.EQUAL, Separator.AND);
        builder.addOrder("integerField", 1);
        builder.addDistinct("stringField", 1);
        builder.addField("stringField");

        List<QueryRow> rows = FastPojoFilterSupport.tryFilterRows(builder);

        assertFalse(rows.isEmpty());
        assertEquals(List.of("stringField", "integerField"), rowFieldNames(rows.get(0)));
    }

    @Test
    void tryFilterRowsShouldKeepFullSchemaForOpenEndedProjection() {
        FilterQueryBuilder builder = new FilterQueryBuilder(sampleFoos());
        builder.addRule("stringField", "alpha", Clauses.EQUAL, Separator.AND);

        List<QueryRow> rows = FastPojoFilterSupport.tryFilterRows(builder);

        assertFalse(rows.isEmpty());
        assertEquals(List.of("stringField", "dateField", "integerField"), rowFieldNames(rows.get(0)));
    }

    private static List<String> rowFieldNames(QueryRow row) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < row.getFields().size(); i++) {
            names.add(row.getFields().get(i).getFieldName());
        }
        return names;
    }
}
