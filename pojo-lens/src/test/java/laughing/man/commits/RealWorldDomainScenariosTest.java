package laughing.man.commits;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.CompanyEmployee;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import laughing.man.commits.testutil.BusinessFixtures.EmployeeSummary;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanyEmployees;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;

public class RealWorldDomainScenariosTest {

    @Test
    public void fluentFilterShouldReturnActiveEngineeringEmployeesOrderedBySalary() throws Exception {
        List<Employee> employees = sampleEmployees();

        List<Employee> results = PojoLens.newQueryBuilder(employees)
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addRule("active", true, Clauses.EQUAL, Separator.AND)
                .addOrder("salary", 1)
                .initFilter()
                .filter(Sort.DESC, Employee.class);

        assertEquals(2, results.size());
        assertEquals("Cara", results.get(0).name);
        assertEquals("Alice", results.get(1).name);
    }

    @Test
    public void sqlLikeShouldMatchFluentForDepartmentAndSalaryFilter() throws Exception {
        List<Employee> employees = sampleEmployees();

        List<Employee> fluent = PojoLens.newQueryBuilder(employees)
                .addRule("department", "Engineering", Clauses.EQUAL, Separator.AND)
                .addRule("salary", 120000, Clauses.BIGGER_EQUAL, Separator.AND)
                .addOrder("salary", 1)
                .initFilter()
                .filter(Sort.ASC, Employee.class);

        List<Employee> sqlLike = PojoLens
                .parse("select name, salary where department = 'Engineering' and salary >= 120000 order by salary asc")
                .filter(employees, Employee.class);

        assertEquals(fluent.size(), sqlLike.size());
        assertEquals(fluent.get(0).salary, sqlLike.get(0).salary);
        assertEquals(fluent.get(1).salary, sqlLike.get(1).salary);
    }

    @Test
    public void aliasProjectionShouldMapToBusinessDtoFields() {
        List<Employee> employees = sampleEmployees();

        List<EmployeeSummary> summaries = PojoLens
                .parse("select name as employeeName, salary as annualSalary where active = true order by salary asc")
                .filter(employees, EmployeeSummary.class);

        assertEquals(3, summaries.size());
        Map<String, Integer> salaryByName = summaries.stream()
                .collect(Collectors.toMap(s -> s.employeeName, s -> s.annualSalary));
        assertEquals(Integer.valueOf(90000), salaryByName.get("Bob"));
        assertEquals(Integer.valueOf(120000), salaryByName.get("Alice"));
        assertEquals(Integer.valueOf(130000), salaryByName.get("Cara"));
    }

    @Test
    public void sqlLikeJoinShouldFilterCompaniesByEmployeeTitle() {
        List<Company> companies = sampleCompanies();
        List<CompanyEmployee> companyEmployees = sampleCompanyEmployees();
        Map<String, List<?>> joinSources = new HashMap<>();
        joinSources.put("employees", companyEmployees);

        List<Company> filtered = PojoLens
                .parse("select * from companies left join employees on id = companyId where title = 'Engineer'")
                .filter(companies, joinSources, Company.class);

        assertEquals(1, filtered.size());
        assertEquals("Acme", filtered.get(0).name);
    }

    @Test
    public void bindFirstExecuteLaterShouldWorkForRealWorldDataset() {
        List<Employee> employees = sampleEmployees();

        SqlLikeQuery query = PojoLens.parse("where salary >= 100000 order by salary asc");
        List<Employee> rows = query.bindTyped(employees, Employee.class).filter();

        assertEquals(3, rows.size());
        assertEquals(110000, rows.get(0).salary);
        assertEquals(120000, rows.get(1).salary);
        assertEquals(130000, rows.get(2).salary);
    }
}

