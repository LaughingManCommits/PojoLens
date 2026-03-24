package laughing.man.commits;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.sqllike.SqlLikeLintCodes;
import laughing.man.commits.testing.FluentSqlLikeParity;
import laughing.man.commits.testing.QueryRegressionFixture;
import laughing.man.commits.testing.QuerySnapshotFixture;
import laughing.man.commits.testutil.BusinessFixtures.Company;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static laughing.man.commits.testutil.BusinessFixtures.sampleCompanies;
import static laughing.man.commits.testutil.BusinessFixtures.sampleEmployees;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QueryRegressionFixtureTest {

    @Test
    public void sqlFixtureShouldAssertRowsMetricsExplainAndLint() {
        QuerySnapshotFixture employees = QuerySnapshotFixture.of("employees-active", sampleEmployees());

        QueryRegressionFixture<Employee> activeEmployees = QueryRegressionFixture.sql(
                employees,
                PojoLens.parse("where active = true order by salary desc limit 2"),
                Employee.class
        );

        activeEmployees
                .assertRowCount(2)
                .assertMetric("topSalary", rows -> rows.get(0).salary, 130000)
                .assertOrderedRows(row -> row.name + ":" + row.salary,
                        "Cara:130000",
                        "Alice:120000")
                .assertExplainContains(Map.of(
                        "normalizedQuery", "where active = true order by salary desc limit 2",
                        "limit", 2,
                        "stageRowCounts", Map.of("limit", Map.of("after", 2))
                ));

        QueryRegressionFixture<Company> lintFixture = QueryRegressionFixture.sql(
                QuerySnapshotFixture.of("companies-lint", sampleCompanies()),
                PojoLens.parse("select * from companies limit 1").lintMode(),
                Company.class
        );

        lintFixture.assertLintCodes(
                SqlLikeLintCodes.SELECT_WILDCARD,
                SqlLikeLintCodes.LIMIT_WITHOUT_ORDER
        );
    }

    @Test
    public void reportFixtureShouldSupportNamedSnapshotRowAssertions() {
        QuerySnapshotFixture employees = QuerySnapshotFixture.of("employees-report", sampleEmployees());
        QueryRegressionFixture<DepartmentCountRow> reportFixture = QueryRegressionFixture.report(
                employees,
                PojoLens.report(
                        PojoLens.parse("select department, count(*) as total group by department order by department asc"),
                        DepartmentCountRow.class
                )
        );

        reportFixture
                .assertRowCount(2)
                .assertUnorderedRows(row -> row.department + ":" + row.total,
                        "Engineering:3",
                        "Finance:1")
                .assertMetric("departmentCount", rows -> rows.size(), 2);
    }

    @Test
    public void fixtureBackedParityShouldReuseNamedSnapshot() {
        QuerySnapshotFixture employees = QuerySnapshotFixture.of("employees-parity", sampleEmployees());

        FluentSqlLikeParity.assertOrderedEquals(
                employees,
                Employee.class,
                builder -> builder
                        .addRule("active", true, Clauses.EQUAL, Separator.AND)
                        .addOrder("salary", 1)
                        .limit(2),
                PojoLens.parse("where active = true order by salary desc limit 2"),
                row -> row.name + ":" + row.salary
        );
    }

    @Test
    public void unsupportedExplainOrLintShouldFailClearly() {
        QueryRegressionFixture<Employee> fluentFixture = QueryRegressionFixture.fluent(
                QuerySnapshotFixture.of("employees-fluent", sampleEmployees()),
                Employee.class,
                builder -> builder.addRule("active", true, Clauses.EQUAL)
        );

        assertThrows(IllegalStateException.class, fluentFixture::explain);
        assertThrows(IllegalStateException.class, fluentFixture::lintCodes);
    }

    public static class DepartmentCountRow {
        public String department;
        public long total;

        public DepartmentCountRow() {
        }
    }
}

