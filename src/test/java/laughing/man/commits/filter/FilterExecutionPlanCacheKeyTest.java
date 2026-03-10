package laughing.man.commits.filter;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import org.junit.jupiter.api.Test;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FilterExecutionPlanCacheKeyTest {

    @Test
    void structuralKeyShouldIgnoreGeneratedRuleIdsForEquivalentQueries() {
        FilterQueryBuilder first = new FilterQueryBuilder(sampleEmployees())
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total");
        FilterQueryBuilder second = new FilterQueryBuilder(sampleEmployees())
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total");

        assertEquals(FilterExecutionPlanCacheKey.from(first), FilterExecutionPlanCacheKey.from(second));
    }

    @Test
    void structuralKeyShouldChangeWhenRuleValuesChange() {
        FilterQueryBuilder first = new FilterQueryBuilder(sampleEmployees())
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total");
        FilterQueryBuilder second = new FilterQueryBuilder(sampleEmployees())
                .addRule("department", "Finance", Clauses.EQUAL, Separator.AND)
                .addGroup("department")
                .addCount("total");

        assertNotEquals(FilterExecutionPlanCacheKey.from(first), FilterExecutionPlanCacheKey.from(second));
    }
}
