package laughing.man.commits.util;

import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionUtilTest {

    @Test
    void newUuidShouldUseUnderscoresAndDigitFreeAlphabeticEncoding() {
        String id = ReflectionUtil.newUUID();

        assertEquals(36, id.length());
        assertEquals('_', id.charAt(8));
        assertEquals('_', id.charAt(13));
        assertEquals('_', id.charAt(18));
        assertEquals('_', id.charAt(23));
        assertFalse(id.contains("-"));
        assertTrue(id.chars().noneMatch(Character::isDigit));
    }

    @Test
    void newUuidShouldRemainUniqueAcrossCalls() {
        String first = ReflectionUtil.newUUID();
        String second = ReflectionUtil.newUUID();

        assertNotEquals(first, second);
    }

    @Test
    void toDomainRowsShouldPreserveQueryableFieldOrderForNestedBeans() {
        RootBean bean = new RootBean(7, "alpha", new Address("Amsterdam", new Geo("NL", 1011)));

        List<QueryRow> rows = ReflectionUtil.toDomainRows(List.of(bean));
        List<String> fieldNames = rows.get(0).getFields().stream().map(QueryField::getFieldName).toList();
        List<Object> values = rows.get(0).getFields().stream().map(QueryField::getValue).toList();

        assertEquals(ReflectionUtil.collectQueryableFieldNames(RootBean.class), fieldNames);
        assertEquals(List.of(7, "alpha", "Amsterdam", "NL", 1011), values);
    }

    @Test
    void toDomainRowsShouldHandleNestedCyclesWithoutInfiniteRecursion() {
        Manager manager = new Manager("lead");
        Person person = new Person("alice", manager);
        manager.owner = person;

        List<QueryRow> rows = ReflectionUtil.toDomainRows(List.of(person));
        List<? extends QueryField> fields = rows.get(0).getFields();

        assertEquals(List.of("name", "manager.title"), fields.stream().map(QueryField::getFieldName).toList());
        assertEquals("alice", fields.get(0).getValue());
        assertEquals("lead", fields.get(1).getValue());
    }

    @Test
    void toDomainRowsShouldReturnNullForMissingNestedObjects() {
        RootBean bean = new RootBean(7, "alpha", null);

        List<QueryRow> rows = ReflectionUtil.toDomainRows(List.of(bean));
        List<? extends QueryField> fields = rows.get(0).getFields();

        assertEquals(List.of("id", "name", "address.city", "address.geo.countryCode", "address.geo.zipCode"),
                fields.stream().map(QueryField::getFieldName).toList());
        assertNull(fields.get(2).getValue());
        assertNull(fields.get(3).getValue());
        assertNull(fields.get(4).getValue());
    }

    @Test
    void toClassListShouldMaterializeNestedProjectionFromQueryRows() {
        RootBean bean = new RootBean(7, "alpha", new Address("Amsterdam", new Geo("NL", 1011)));

        List<QueryRow> rows = ReflectionUtil.toDomainRows(List.of(bean));
        List<ProjectedRootBean> projected = ReflectionUtil.toClassList(ProjectedRootBean.class, rows);

        assertEquals(1, projected.size());
        assertEquals(7, projected.get(0).id);
        assertEquals("alpha", projected.get(0).name);
        assertNotNull(projected.get(0).address);
        assertEquals("Amsterdam", projected.get(0).address.city);
        assertNotNull(projected.get(0).address.geo);
        assertEquals("NL", projected.get(0).address.geo.countryCode);
        assertEquals(1011, projected.get(0).address.geo.zipCode);
    }

    @Test
    void toClassListShouldMaterializeNestedProjectionFromArrayRows() {
        List<Object[]> rows = List.<Object[]>of(new Object[]{7, "alpha", "Amsterdam", "NL", 1011});

        List<ProjectedRootBean> projected = ReflectionUtil.toClassList(
                ProjectedRootBean.class,
                rows,
                List.of("id", "name", "address.city", "address.geo.countryCode", "address.geo.zipCode")
        );

        assertEquals(1, projected.size());
        assertEquals(7, projected.get(0).id);
        assertEquals("alpha", projected.get(0).name);
        assertNotNull(projected.get(0).address);
        assertEquals("Amsterdam", projected.get(0).address.city);
        assertNotNull(projected.get(0).address.geo);
        assertEquals("NL", projected.get(0).address.geo.countryCode);
        assertEquals(1011, projected.get(0).address.geo.zipCode);
    }

    @Test
    void toClassListShouldReuseProjectionPlanForRepeatedSchema() throws Exception {
        int before = projectionPlanCache().size();
        List<QueryRow> rows = List.of(queryRow(
                queryField("alpha", 3),
                queryField("nested.beta", "value"),
                queryField("ignored", "skip")
        ));

        List<ProjectionPlanProbe> first = ReflectionUtil.toClassList(ProjectionPlanProbe.class, rows);
        int afterFirst = projectionPlanCache().size();
        List<ProjectionPlanProbe> second = ReflectionUtil.toClassList(ProjectionPlanProbe.class, rows);
        int afterSecond = projectionPlanCache().size();

        assertEquals(before + 1, afterFirst);
        assertEquals(afterFirst, afterSecond);
        assertEquals(3, first.get(0).alpha);
        assertEquals("value", first.get(0).nested.beta);
        assertEquals(3, second.get(0).alpha);
        assertEquals("value", second.get(0).nested.beta);
    }

    @Test
    void toClassListShouldSkipLeadingEmptyRowsWhenDerivingProjectionSchema() {
        QueryRow empty = new QueryRow();
        empty.setFields(List.of());
        QueryRow populated = queryRow(
                queryField("alpha", 3),
                queryField("nested.beta", "value")
        );

        List<ProjectionPlanProbe> projected = ReflectionUtil.toClassList(
                ProjectionPlanProbe.class,
                List.of(empty, populated)
        );

        assertEquals(2, projected.size());
        assertEquals(0, projected.get(0).alpha);
        assertNull(projected.get(0).nested);
        assertEquals(3, projected.get(1).alpha);
        assertNotNull(projected.get(1).nested);
        assertEquals("value", projected.get(1).nested.beta);
    }

    @Test
    void compileFlatRowReadPlanShouldReuseCacheForEquivalentSelection() throws Exception {
        int before = flatRowReadPlanCache().size();

        ReflectionUtil.FlatRowReadPlan first = ReflectionUtil.compileFlatRowReadPlan(
                RootBean.class,
                List.of("id", "address.geo.zipCode")
        );
        int afterFirst = flatRowReadPlanCache().size();
        ReflectionUtil.FlatRowReadPlan second = ReflectionUtil.compileFlatRowReadPlan(
                RootBean.class,
                List.of("id", "address.geo.zipCode")
        );
        int afterSecond = flatRowReadPlanCache().size();

        assertEquals(before + 1, afterFirst);
        assertEquals(afterFirst, afterSecond);
        assertTrue(first == second);
        assertEquals(List.of("id", "address.geo.zipCode"), first.fieldNames());
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> projectionPlanCache() throws Exception {
        Field field = ReflectionUtil.class.getDeclaredField("PROJECTION_WRITE_PLAN_CACHE");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> flatRowReadPlanCache() throws Exception {
        Field field = ReflectionUtil.class.getDeclaredField("FLAT_ROW_READ_PLAN_CACHE");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }

    private static QueryRow queryRow(QueryField... fields) {
        QueryRow row = new QueryRow();
        row.setFields(List.of(fields));
        return row;
    }

    private static QueryField queryField(String name, Object value) {
        QueryField field = new QueryField();
        field.setFieldName(name);
        field.setValue(value);
        return field;
    }

    static final class RootBean {
        int id;
        String name;
        Address address;

        RootBean(int id, String name, Address address) {
            this.id = id;
            this.name = name;
            this.address = address;
        }
    }

    static final class Address {
        String city;
        Geo geo;

        Address(String city, Geo geo) {
            this.city = city;
            this.geo = geo;
        }
    }

    static final class Geo {
        String countryCode;
        int zipCode;

        Geo(String countryCode, int zipCode) {
            this.countryCode = countryCode;
            this.zipCode = zipCode;
        }
    }

    static final class Person {
        String name;
        Manager manager;

        Person(String name, Manager manager) {
            this.name = name;
            this.manager = manager;
        }
    }

    static final class Manager {
        String title;
        Person owner;

        Manager(String title) {
            this.title = title;
        }
    }

    static final class ProjectedRootBean {
        int id;
        String name;
        ProjectedAddress address;

        ProjectedRootBean() {
        }
    }

    static final class ProjectedAddress {
        String city;
        ProjectedGeo geo;

        ProjectedAddress() {
        }
    }

    static final class ProjectedGeo {
        String countryCode;
        int zipCode;

        ProjectedGeo() {
        }
    }

    static final class ProjectionPlanProbe {
        int alpha;
        ProbeNested nested;

        ProjectionPlanProbe() {
        }
    }

    static final class ProbeNested {
        String beta;

        ProbeNested() {
        }
    }
}


