package laughing.man.commits.sqllike;

import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.PojoLensSql;
import laughing.man.commits.natural.NaturalVocabulary;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;

import org.junit.jupiter.api.Test;

import java.util.List;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryExposurePolicyTest {

    @Test
    public void allowedSqlLikeFieldsShouldExecute() {
        QueryExposurePolicy policy = QueryExposurePolicy.builder()
                .allowFields("name", "department", "active")
                .build();

        List<EmployeeSummary> rows = PojoLensSql
                .parse("select name as employeeName where department = 'Engineering' and active = true")
                .exposurePolicy(policy)
                .filter(sampleEmployees(), EmployeeSummary.class);

        assertEquals(List.of("Alice", "Cara"), rows.stream().map(row -> row.employeeName).toList());
    }

    @Test
    public void blockedSqlLikeFieldShouldAppearInDiagnosticsAndExecution() {
        QueryExposurePolicy policy = QueryExposurePolicy.builder()
                .allowFields("name", "department")
                .build();

        SqlLikeQuery query = PojoLensSql
                .parse("select name as employeeName where salary >= 100000")
                .exposurePolicy(policy);

        QueryDiagnostics diagnostics = query.diagnostics(Employee.class, EmployeeSummary.class);

        assertFalse(diagnostics.valid());
        assertTrue(diagnostics.errors().stream()
                .anyMatch(error -> SqlLikeErrorCodes.EXPOSURE_FIELD_BLOCKED.equals(error.code())
                        && error.message().contains("salary")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> query.filter(sampleEmployees(), EmployeeSummary.class));
        assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.EXPOSURE_FIELD_BLOCKED));
        assertTrue(ex.getMessage().contains("salary"));
    }

    @Test
    public void wildcardSelectShouldHonorFieldExposurePolicy() {
        QueryExposurePolicy policy = QueryExposurePolicy.builder()
                .allowFields("name")
                .build();

        QueryDiagnostics diagnostics = PojoLensSql
                .parse("select * where name = 'Alice'")
                .exposurePolicy(policy)
                .diagnostics(Employee.class, Employee.class);

        assertFalse(diagnostics.valid());
        assertTrue(diagnostics.errors().stream()
                .anyMatch(error -> SqlLikeErrorCodes.EXPOSURE_FIELD_BLOCKED.equals(error.code())
                        && error.message().contains("salary")));
    }

    @Test
    public void blockedJoinSourceShouldAppearInDiagnosticsAndExecution() {
        QueryExposurePolicy policy = QueryExposurePolicy.builder()
                .allowSources("companies")
                .build();
        JoinBindings employees = JoinBindings.of("employees", sampleCompanyEmployees());

        SqlLikeQuery query = PojoLensSql
                .parse("select * from companies left join employees on id = companyId")
                .exposurePolicy(policy);

        QueryDiagnostics diagnostics = query.diagnostics(Company.class, Company.class, employees);

        assertFalse(diagnostics.valid());
        assertTrue(diagnostics.errors().stream()
                .anyMatch(error -> SqlLikeErrorCodes.EXPOSURE_SOURCE_BLOCKED.equals(error.code())
                        && error.message().contains("employees")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> query.filter(sampleCompanies(), employees, Company.class));
        assertTrue(ex.getMessage().contains(SqlLikeErrorCodes.EXPOSURE_SOURCE_BLOCKED));
        assertTrue(ex.getMessage().contains("employees"));
    }

    @Test
    public void allowedJoinSourcesShouldExecute() {
        QueryExposurePolicy policy = QueryExposurePolicy.builder()
                .allowSources("companies", "employees")
                .build();

        List<Company> rows = PojoLensSql
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .exposurePolicy(policy)
                .filter(sampleCompanies(), JoinBindings.of("employees", sampleCompanyEmployees()), Company.class);

        assertEquals(List.of("Acme"), rows.stream().map(row -> row.name).toList());
    }

    @Test
    public void blockedSubquerySourceShouldAppearInDiagnostics() {
        QueryExposurePolicy policy = QueryExposurePolicy.builder()
                .allowSources("companies")
                .build();

        QueryDiagnostics diagnostics = PojoLensSql
                .parse("where exists (select * from employees where title = 'Engineer')")
                .exposurePolicy(policy)
                .diagnostics(
                        Company.class,
                        Company.class,
                        JoinBindings.of("employees", sampleCompanyEmployees())
                );

        assertFalse(diagnostics.valid());
        assertTrue(diagnostics.errors().stream()
                .anyMatch(error -> SqlLikeErrorCodes.EXPOSURE_SOURCE_BLOCKED.equals(error.code())
                        && error.message().contains("employees")));
    }

    @Test
    public void runtimePolicyShouldApplyToSqlLikeAndNaturalQueries() {
        QueryExposurePolicy policy = QueryExposurePolicy.builder()
                .allowFields("name", "salary", "active")
                .build();
        PojoLensRuntime runtime = new PojoLensRuntime();
        runtime.setQueryExposurePolicy(policy);
        runtime.setNaturalVocabulary(NaturalVocabulary.builder()
                .field("salary", "pay")
                .build());

        List<EmployeeSummary> sqlRows = runtime
                .parse("select name as employeeName where salary >= 120000 and active = true")
                .filter(sampleEmployees(), EmployeeSummary.class);
        assertEquals(List.of("Alice", "Cara"), sqlRows.stream().map(row -> row.employeeName).toList());

        List<Employee> naturalRows = runtime.natural()
                .parse("show name where pay is greater than 119999 and active is true")
                .filter(sampleEmployees(), Employee.class);
        assertEquals(List.of("Alice", "Cara"), naturalRows.stream().map(row -> row.name).toList());
    }
}
