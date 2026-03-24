package laughing.man.commits.query;

import laughing.man.commits.PojoLens;
import laughing.man.commits.annotations.Exclude;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Sort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NestedPathQueryTest {

    @Test
    public void fluentFilterShouldSupportNestedDottedPathsAndRoundTripToPojo() {
        List<Person> rows = PojoLens.newQueryBuilder(samplePeople())
                .addRule("address.country", "NL", Clauses.EQUAL)
                .addRule("address.city", "Amsterdam", Clauses.EQUAL)
                .addOrder("address.zipCode", 1)
                .initFilter()
                .filter(Sort.ASC, Person.class);

        assertEquals(2, rows.size());
        assertEquals("Ana", rows.get(0).name);
        assertEquals("Bram", rows.get(1).name);
        assertNotNull(rows.get(0).address);
        assertEquals("Amsterdam", rows.get(0).address.city);
        assertEquals(1011, rows.get(0).address.zipCode);
        assertEquals("NL", rows.get(1).address.country);
    }

    @Test
    public void sqlLikeShouldProjectAndGroupUsingNestedDottedPaths() {
        List<PersonCityRow> projected = PojoLens
                .parse("select name, address.city as city where address.country = 'NL' order by address.zipCode asc")
                .filter(samplePeople(), PersonCityRow.class);

        assertEquals(3, projected.size());
        assertEquals("Ana", projected.get(0).name);
        assertEquals("Amsterdam", projected.get(0).city);
        assertEquals("Daan", projected.get(2).name);
        assertEquals("Rotterdam", projected.get(2).city);

        List<CountryCountRow> grouped = PojoLens
                .parse("select address.country as country, count(*) as total "
                        + "where address.country != null group by address.country order by address.country asc")
                .filter(samplePeople(), CountryCountRow.class);

        assertEquals(2, grouped.size());
        assertEquals("DE", grouped.get(0).country);
        assertEquals(1L, grouped.get(0).total);
        assertEquals("NL", grouped.get(1).country);
        assertEquals(3L, grouped.get(1).total);
    }

    @Test
    public void sqlLikeShouldRejectExcludedNestedPaths() {
        try {
            PojoLens.parse("where address.secretCode = 's1'").filter(samplePeople(), Person.class);
            fail("Expected excluded nested field validation error");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Unknown field 'address.secretCode'"));
        }
    }

    private static List<Person> samplePeople() {
        return Arrays.asList(
                new Person("Ana", new Address("Amsterdam", "NL", 1011, "s1")),
                new Person("Bram", new Address("Amsterdam", "NL", 1105, "s2")),
                new Person("Clara", new Address("Berlin", "DE", 10999, "s3")),
                new Person("Daan", new Address("Rotterdam", "NL", 3011, "s4")),
                new Person("Evi", null)
        );
    }

    public static class Person {
        String name;
        Address address;

        public Person() {
        }

        public Person(String name, Address address) {
            this.name = name;
            this.address = address;
        }
    }

    public static class Address {
        String city;
        String country;
        int zipCode;
        @Exclude
        String secretCode;

        public Address() {
        }

        public Address(String city, String country, int zipCode, String secretCode) {
            this.city = city;
            this.country = country;
            this.zipCode = zipCode;
            this.secretCode = secretCode;
        }
    }

    public static class PersonCityRow {
        String name;
        String city;

        public PersonCityRow() {
        }
    }

    public static class CountryCountRow {
        String country;
        long total;

        public CountryCountRow() {
        }
    }
}

