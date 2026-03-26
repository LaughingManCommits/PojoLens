package laughing.man.commits.publicapi;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.builder.QueryBuilder;
import laughing.man.commits.filter.Filter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PublicSurfaceContractTest {

    @Test
    public void queryBuilderInterfaceShouldExposeStatsMethods() {
        Set<String> methods = publicMethodNames(QueryBuilder.class);
        assertTrue(methods.contains("addMetric"));
        assertTrue(methods.contains("addCount"));
        assertTrue(methods.contains("addTimeBucket"));
        assertTrue(methods.contains("copyOnBuild"));
        assertTrue(methods.contains("limit"));
        assertTrue(methods.contains("explain"));
        assertTrue(methods.contains("schema"));
        assertTrue(methods.contains("computedFields"));
        assertTrue(methods.contains("addDistinct"));
        assertTrue(methods.contains("addJoinBeans"));
    }

    @Test
    public void filterQueryBuilderShouldNotExposeRemovedStateMutators() {
        Set<String> methods = declaredPublicMethodNames(FilterQueryBuilder.class);
        assertFalse(methods.contains("setLimit"));
        assertFalse(methods.contains("setGroupFields"));
        assertFalse(methods.contains("setOrderFields"));
        assertFalse(methods.contains("setDistinctFields"));
        assertFalse(methods.contains("setFilterValues"));
        assertFalse(methods.contains("setFilterFields"));
        assertFalse(methods.contains("setFilterClause"));
        assertFalse(methods.contains("setFilterSeparator"));
        assertFalse(methods.contains("setFilterDateFormats"));
        assertFalse(methods.contains("setFilterIDs"));
        assertFalse(methods.contains("setJoinClasses"));
        assertFalse(methods.contains("setJoinMethods"));
        assertFalse(methods.contains("setJoinParentFields"));
        assertFalse(methods.contains("setJoinChildFields"));
        assertFalse(methods.contains("setReturnFields"));
    }

    @Test
    public void filterJoinShouldReturnFilterContract() throws Exception {
        Method method = Filter.class.getMethod("join");
        assertEquals(Filter.class, method.getReturnType());
    }

    private static Set<String> publicMethodNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Method method : type.getMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static Set<String> declaredPublicMethodNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                names.add(method.getName());
            }
        }
        return names;
    }
}

