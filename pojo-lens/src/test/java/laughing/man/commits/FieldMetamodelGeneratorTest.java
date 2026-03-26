package laughing.man.commits;

import laughing.man.commits.annotations.Exclude;
import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.chart.ChartType;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Sort;
import laughing.man.commits.metamodel.FieldMetamodel;
import laughing.man.commits.metamodel.FieldMetamodelGenerator;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldMetamodelGeneratorTest {

    @Test
    public void generateShouldExposeDeterministicFieldsAndSource() {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generate(DepartmentPayrollRow.class);

        assertEquals(DepartmentPayrollRow.class, metamodel.modelClass());
        assertEquals("laughing.man.commits", metamodel.packageName());
        assertEquals("DepartmentPayrollRowFields", metamodel.simpleName());
        assertEquals(Arrays.asList("active", "department", "payroll"), metamodel.fieldNames());
        assertEquals("department", metamodel.constants().get("DEPARTMENT"));
        assertEquals("payroll", metamodel.constants().get("PAYROLL"));
        assertFalse(metamodel.source().contains("INTERNAL_ONLY"));
        assertTrue(metamodel.source().contains("public static final String DEPARTMENT = \"department\";"));
        assertTrue(metamodel.source().contains("public static final List<String> ALL = List.of("));
    }

    @Test
    public void generatedSourceShouldCompileAndWorkWithBuilderAndCharts() throws Exception {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generate(
                DepartmentPayrollRow.class,
                "laughing.man.commits.generated",
                "DepartmentPayrollFields"
        );

        Path sourceRoot = Files.createTempDirectory("pojo-lens-metamodel-src");
        Path javaFile = metamodel.writeTo(sourceRoot);
        Path classesRoot = Files.createTempDirectory("pojo-lens-metamodel-classes");
        compile(javaFile, classesRoot);

        try (URLClassLoader loader = new URLClassLoader(new URL[]{classesRoot.toUri().toURL()})) {
            Class<?> fieldsClass = loader.loadClass("laughing.man.commits.generated.DepartmentPayrollFields");
            String activeField = (String) fieldsClass.getField("ACTIVE").get(null);
            String departmentField = (String) fieldsClass.getField("DEPARTMENT").get(null);
            String payrollField = (String) fieldsClass.getField("PAYROLL").get(null);

            @SuppressWarnings("unchecked")
            List<String> allFields = (List<String>) fieldsClass.getField("ALL").get(null);
            assertEquals(Arrays.asList("active", "department", "payroll"), allFields);

            List<DepartmentPayrollRow> filtered = PojoLensCore.newQueryBuilder(sampleRows())
                    .addRule(activeField, true, Clauses.EQUAL)
                    .addOrder(payrollField, 1)
                    .initFilter()
                    .filter(Sort.DESC, DepartmentPayrollRow.class);

            assertEquals(2, filtered.size());
            assertEquals("Engineering", filtered.get(0).department);
            assertEquals(250000d, filtered.get(0).payroll, 0.0001d);

            ChartData chart = PojoLensChart.toChartData(filtered, ChartSpec.of(ChartType.BAR, departmentField, payrollField));
            assertEquals(Arrays.asList("Engineering", "Finance"), chart.getLabels());
            assertEquals(1, chart.getDatasets().size());
            assertEquals(250000d, chart.getDatasets().get(0).getValues().get(0), 0.0001d);
            assertEquals(90000d, chart.getDatasets().get(0).getValues().get(1), 0.0001d);
        }
    }

    @Test
    public void generateShouldIncludeQueryableNestedPaths() {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generate(NestedEmployee.class);

        assertTrue(metamodel.fieldNames().contains("location.city"));
        assertTrue(metamodel.fieldNames().contains("location.country"));
        assertEquals("location.city", metamodel.constants().get("LOCATION_CITY"));
        assertEquals("location.country", metamodel.constants().get("LOCATION_COUNTRY"));
        assertFalse(metamodel.fieldNames().contains("location.secretCode"));
    }

    private static void compile(Path javaFile, Path classesRoot) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required for metamodel generator tests");

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(javaFile.toFile());
            boolean compiled = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    List.of("-d", classesRoot.toString()),
                    null,
                    units
            ).call();
            assertTrue(compiled, "Generated metamodel source should compile");
        }
    }

    private static List<DepartmentPayrollRow> sampleRows() {
        return Arrays.asList(
                new DepartmentPayrollRow("Engineering", 250000d, true, "ignore"),
                new DepartmentPayrollRow("Finance", 90000d, true, "ignore"),
                new DepartmentPayrollRow("Support", 40000d, false, "ignore")
        );
    }

    public static final class DepartmentPayrollRow {
        public String department;
        public double payroll;
        public boolean active;
        @Exclude
        public String internalOnly;

        public DepartmentPayrollRow(String department, double payroll, boolean active, String internalOnly) {
            this.department = department;
            this.payroll = payroll;
            this.active = active;
            this.internalOnly = internalOnly;
        }

        public DepartmentPayrollRow() {
        }
    }

    public static final class NestedEmployee {
        public String name;
        public Location location;

        public NestedEmployee() {
        }
    }

    public static final class Location {
        public String city;
        public String country;
        @Exclude
        public String secretCode;

        public Location() {
        }
    }
}

