package laughing.man.commits;

import laughing.man.commits.computed.ComputedFieldDefinition;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.computed.internal.ComputedFieldSupport;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.dsl.TypedField;
import laughing.man.commits.dsl.TypedQuery;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-29 slice 3: computed fields with text, date/time, and enum output types.
 */
class NonNumericComputedFieldTest {

    private static final ComputedFieldRegistry TEXT_FIELDS = ComputedFieldRegistry.builder()
            .add("label", "concat(name, ' / ', department)", String.class)
            .add("deptKey", "lower(department)", String.class)
            .build();

    private enum Level {
        JUNIOR,
        SENIOR
    }

    @Test
    void textFieldsFilterSortAndProject() {
        List<Labelled> rows = PojoLensSql
                .parse("select name, label where label like '%Engineering' order by label desc")
                .computedFields(TEXT_FIELDS)
                .filter(sampleEmployees(), Labelled.class);

        assertEquals(List.of("Dan / Engineering", "Cara / Engineering", "Alice / Engineering"),
                rows.stream().map(row -> row.label).toList());
    }

    @Test
    void textFieldsGroupRows() {
        List<DepartmentCount> counts = PojoLensSql
                .parse("select deptKey, count(*) as total group by deptKey order by deptKey")
                .computedFields(TEXT_FIELDS)
                .filter(sampleEmployees(), DepartmentCount.class);

        assertEquals(List.of("engineering", "finance"), counts.stream().map(row -> row.deptKey).toList());
        assertEquals(List.of(3L, 1L), counts.stream().map(row -> row.total).toList());
    }

    @Test
    void dateAndEnumFieldsKeepTheirTypes() {
        ComputedFieldRegistry registry = ComputedFieldRegistry.builder()
                .add("lastDay", "coalesce(endDate, startDate)", LocalDate.class)
                .add("effectiveLevel", "coalesce(level, fallback)", Level.class)
                .build();
        List<Contract> contracts = List.of(
                new Contract("a", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 6, 30), null, Level.SENIOR),
                new Contract("b", LocalDate.of(2025, 2, 1), null, Level.JUNIOR, Level.SENIOR),
                new Contract("c", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), Level.SENIOR, null));

        List<String> names = PojoLensSql
                .parse("where lastDay >= '2025-01-01' and effectiveLevel = 'SENIOR'")
                .computedFields(registry)
                .filter(contracts, Contract.class)
                .stream().map(contract -> contract.name).toList();
        List<QueryRow> rows = PojoLensSql.parse("select name, lastDay, effectiveLevel where name = 'b'")
                .computedFields(registry)
                .filter(contracts, QueryRow.class);

        assertEquals(List.of("a"), names);
        assertEquals(LocalDate.of(2025, 2, 1), rows.get(0).getFields().get(1).getValue());
        assertEquals(Level.JUNIOR, rows.get(0).getFields().get(2).getValue());
    }

    @Test
    void typedAndNaturalQueriesReadTextFields() {
        List<Employee> typed = TypedQuery.from(Employee.class)
                .computedFields(TEXT_FIELDS)
                .where(TypedField.<Employee, String>of("deptKey", String.class).eq("finance"))
                .filter(sampleEmployees());
        List<Employee> natural = PojoLensNatural.parse("show employees where deptKey is 'finance'")
                .computedFields(TEXT_FIELDS)
                .filter(sampleEmployees(), Employee.class);

        assertEquals(List.of("Bob"), typed.stream().map(row -> row.name).toList());
        assertEquals(List.of("Bob"), natural.stream().map(row -> row.name).toList());
    }

    @Test
    void explainReportsTheOutputType() {
        Map<String, Object> explain = PojoLensSql.parse("select name, label").computedFields(TEXT_FIELDS).explain();

        assertEquals(List.of("label:concat(name, ' / ', department):String"), explain.get("computedFields"));
    }

    @Test
    void definitionsRejectOutputTypesThatCannotHoldTheResult() {
        assertEquals("Computed field 'x' returns text (String), which cannot be stored as Integer",
                definitionError("lower(name)", Integer.class));
        assertEquals("Computed field 'x' returns a number (Double), which cannot be stored as String",
                definitionError("salary * 2", String.class));
        assertEquals("Computed field 'x' returns text (String), which cannot be stored as LocalDate",
                definitionError("concat(a, b)", LocalDate.class));
        assertTrue(definitionError("lower(1)", String.class).contains("Function LOWER needs text"));
    }

    @Test
    void queriesCheckDefinitionsAgainstFieldTypes() {
        String functionError = queryError(ComputedFieldRegistry.builder()
                .add("shout", "upper(salary)", String.class)
                .build());
        String outputError = queryError(ComputedFieldRegistry.builder()
                .add("pay", "coalesce(salary, 0)", String.class)
                .build());

        assertTrue(functionError.contains(SqlLikeErrorCodes.VALIDATION_EXPRESSION_REFERENCE), functionError);
        assertTrue(functionError.contains("Computed field 'shout': Function UPPER needs text, not a number"), functionError);
        assertTrue(outputError.contains("Computed field 'pay' returns a number (Integer), which cannot be stored as String"),
                outputError);
    }

    @Test
    void valuesOfUnknownTypeAreCheckedWhenStored() {
        ComputedFieldDefinition definition = ComputedFieldDefinition.of("x", "coalesce(a, b)", Integer.class);

        assertEquals(7, ComputedFieldSupport.outputValue(definition, 7.0d));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ComputedFieldSupport.outputValue(definition, "seven"));
        assertEquals("Computed field 'x' returned String, not Integer", error.getMessage());
    }

    private static String definitionError(String expression, Class<?> outputType) {
        return assertThrows(IllegalArgumentException.class,
                () -> ComputedFieldDefinition.of("x", expression, outputType)).getMessage();
    }

    private static String queryError(ComputedFieldRegistry registry) {
        return assertThrows(IllegalArgumentException.class,
                () -> PojoLensSql.parse("where name = 'Alice'").computedFields(registry)
                        .filter(sampleEmployees(), Employee.class)).getMessage();
    }

    static class Labelled {
        String name;
        String label;

        Labelled() {
        }
    }

    static class DepartmentCount {
        String deptKey;
        long total;

        DepartmentCount() {
        }
    }

    static class Contract {
        String name;
        LocalDate startDate;
        LocalDate endDate;
        Level level;
        Level fallback;

        Contract() {
        }

        Contract(String name, LocalDate startDate, LocalDate endDate, Level level, Level fallback) {
            this.name = name;
            this.startDate = startDate;
            this.endDate = endDate;
            this.level = level;
            this.fallback = fallback;
        }
    }
}
