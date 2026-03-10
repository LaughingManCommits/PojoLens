package laughing.man.commits.util;

import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
}
