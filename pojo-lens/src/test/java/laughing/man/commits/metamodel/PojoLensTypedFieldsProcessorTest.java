package laughing.man.commits.metamodel;

import laughing.man.commits.annotations.Exclude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PojoLensTypedFieldsProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    public void processorGeneratedShapeShouldMatchReflectionGeneratorParityMatrix() throws Exception {
        Path model = writeSource("laughing/man/commits/metamodel/ParityModel.java", """
                package laughing.man.commits.metamodel;

                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.OffsetDateTime;
                import java.time.ZonedDateTime;
                import java.util.Date;
                import laughing.man.commits.annotations.Exclude;
                import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

                @GeneratePojoLensTypedFields(packageName = "com.acme.generated", simpleName = "ParityFields")
                public class ParityModel {
                    public boolean active;
                    public Boolean boxedActive;
                    public byte byteScore;
                    public Byte boxedByteScore;
                    public String collisionValue;
                    public String collision_value;
                    public char grade;
                    public Character boxedGrade;
                    public Date hireDate;
                    public int id;
                    public Integer boxedId;
                    public LocalDate localDate;
                    public LocalDateTime localDateTime;
                    public long longScore;
                    public Long boxedLongScore;
                    public String name;
                    public OffsetDateTime offsetDateTime;
                    public short shortScore;
                    public Short boxedShortScore;
                    public Status status;
                    public double total;
                    public Double boxedTotal;
                    public float utilization;
                    public Float boxedUtilization;
                    public ZonedDateTime zonedDateTime;
                    public Nested nested;
                    public String[] tags;
                    public static String ignoredStatic;
                    public final String ignoredFinal = null;
                    @Exclude
                    public String internal;

                    public enum Status {
                        ACTIVE
                    }

                    public static class Nested {
                        public String city;
                        @Exclude
                        public String secret;
                    }
                }
                """);

        CompilationResult result = compile(List.of(model));

        assertTrue(result.compiled(), () -> result.diagnosticsAsString());
        Path generated = tempDir.resolve("generated/com/acme/generated/ParityFields.java");
        String processorSource = Files.readString(generated);
        FieldMetamodel reflectionModel = FieldMetamodelGenerator.generateTyped(
                ParityModel.class,
                "com.acme.generated",
                "ParityFields");

        assertTrue(processorSource.contains("package com.acme.generated;"));
        assertTrue(processorSource.contains("public final class ParityFields"));
        assertEquals(reflectionModel.fieldNames(), processorFieldNames(processorSource));
        assertEquals(
                new ArrayList<>(FieldMetamodelGenerator.buildConstantMap(reflectionModel.fieldNames()).keySet()),
                processorAllConstants(processorSource));
        assertGeneratedTypedField(processorSource, "ACTIVE", "active", "Boolean");
        assertGeneratedTypedField(processorSource, "BOXED_ACTIVE", "boxedActive", "Boolean");
        assertGeneratedTypedField(processorSource, "BYTE_SCORE", "byteScore", "Byte");
        assertGeneratedTypedField(processorSource, "BOXED_BYTE_SCORE", "boxedByteScore", "Byte");
        assertGeneratedTypedField(processorSource, "COLLISION_VALUE", "collisionValue", "String");
        assertGeneratedTypedField(processorSource, "COLLISION_VALUE_2", "collision_value", "String");
        assertGeneratedTypedField(processorSource, "GRADE", "grade", "Character");
        assertGeneratedTypedField(processorSource, "BOXED_GRADE", "boxedGrade", "Character");
        assertGeneratedTypedField(processorSource, "HIRE_DATE", "hireDate", "Date");
        assertGeneratedTypedField(processorSource, "ID", "id", "Integer");
        assertGeneratedTypedField(processorSource, "BOXED_ID", "boxedId", "Integer");
        assertGeneratedTypedField(processorSource, "LOCAL_DATE", "localDate", "LocalDate");
        assertGeneratedTypedField(processorSource, "LOCAL_DATE_TIME", "localDateTime", "LocalDateTime");
        assertGeneratedTypedField(processorSource, "LONG_SCORE", "longScore", "Long");
        assertGeneratedTypedField(processorSource, "BOXED_LONG_SCORE", "boxedLongScore", "Long");
        assertGeneratedTypedField(processorSource, "NAME", "name", "String");
        assertGeneratedTypedField(processorSource, "OFFSET_DATE_TIME", "offsetDateTime", "OffsetDateTime");
        assertGeneratedTypedField(processorSource, "SHORT_SCORE", "shortScore", "Short");
        assertGeneratedTypedField(processorSource, "BOXED_SHORT_SCORE", "boxedShortScore", "Short");
        assertGeneratedTypedField(processorSource, "STATUS", "status", "Status");
        assertGeneratedTypedField(processorSource, "TOTAL", "total", "Double");
        assertGeneratedTypedField(processorSource, "BOXED_TOTAL", "boxedTotal", "Double");
        assertGeneratedTypedField(processorSource, "UTILIZATION", "utilization", "Float");
        assertGeneratedTypedField(processorSource, "BOXED_UTILIZATION", "boxedUtilization", "Float");
        assertGeneratedTypedField(processorSource, "ZONED_DATE_TIME", "zonedDateTime", "ZonedDateTime");
        assertGeneratedTypedField(processorSource, "NESTED_CITY", "nested.city", "String");
        assertFalse(processorSource.contains("TAGS"), "arrays should match reflection-generator exclusion");
        assertFalse(processorSource.contains("INTERNAL"));
        assertFalse(processorSource.contains("IGNORED_STATIC"));
        assertFalse(processorSource.contains("IGNORED_FINAL"));
        assertFalse(processorSource.contains("SECRET"));
        assertTrue(processorSource.contains("import java.util.Date;"));
        assertTrue(processorSource.contains("import java.time.LocalDate;"));
        assertTrue(processorSource.contains("import java.time.LocalDateTime;"));
        assertTrue(processorSource.contains("import java.time.OffsetDateTime;"));
        assertTrue(processorSource.contains("import java.time.ZonedDateTime;"));
        assertTrue(processorSource.contains("import laughing.man.commits.metamodel.ParityModel.Status;"));
        assertTrue(processorSource.contains("STATUS = TypedField.of(\"status\", Status.class);"));
    }

    @Test
    public void processorShouldGenerateTypedFieldsUsableInSameCompilation() throws Exception {
        Path employee = writeSource("com/acme/Employee.java", """
                package com.acme;

                import laughing.man.commits.annotations.Exclude;
                import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

                @GeneratePojoLensTypedFields(packageName = "com.acme.generated", simpleName = "EmployeeLens")
                public class Employee {
                    public boolean active;
                    public String department;
                    public int salary;
                    public Status status;
                    public Nested nested;
                    public static String ignoredStatic;
                    public final String ignoredFinal = null;
                    @Exclude
                    public String internal;

                    public enum Status {
                        ACTIVE,
                        INACTIVE
                    }

                    public static class Nested {
                        public String city;
                        @Exclude
                        public String secret;
                    }
                }
                """);
        Path useGenerated = writeSource("com/acme/UseGenerated.java", """
                package com.acme;

                import com.acme.generated.EmployeeLens;
                import laughing.man.commits.dsl.TypedField;

                final class UseGenerated {
                    void use() {
                        TypedField<Employee, String> department = EmployeeLens.DEPARTMENT;
                        TypedField<Employee, Integer> salary = EmployeeLens.SALARY;
                        TypedField<Employee, Boolean> active = EmployeeLens.ACTIVE;
                        TypedField<Employee, Employee.Status> status = EmployeeLens.STATUS;
                        TypedField<Employee, String> city = EmployeeLens.NESTED_CITY;
                        int all = EmployeeLens.ALL.size();
                        if (department == null || salary == null || active == null
                                || status == null || city == null || all == 0) {
                            throw new IllegalStateException();
                        }
                    }
                }
                """);

        CompilationResult result = compile(List.of(employee, useGenerated));

        assertTrue(result.compiled(), () -> result.diagnosticsAsString());
        Path generated = tempDir.resolve("generated/com/acme/generated/EmployeeLens.java");
        assertTrue(Files.exists(generated));
        String source = Files.readString(generated);
        assertTrue(source.contains("public final class EmployeeLens"));
        assertTrue(source.contains("TypedField<Employee, Integer> SALARY"));
        assertTrue(source.contains("TypedField<Employee, Status> STATUS"));
        assertTrue(source.contains("TypedField<Employee, String> NESTED_CITY"));
        assertFalse(source.contains("INTERNAL"));
        assertFalse(source.contains("IGNORED_STATIC"));
        assertFalse(source.contains("IGNORED_FINAL"));
        assertFalse(source.contains("SECRET"));
    }

    @Test
    public void processorShouldReportInvalidPackageTargetsAsCompilerDiagnostics() throws Exception {
        Path broken = writeSource("com/acme/Broken.java", """
                package com.acme;

                import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

                @GeneratePojoLensTypedFields(packageName = "com.acme.bad-name")
                public class Broken {
                    public String name;
                }
                """);

        CompilationResult result = compile(List.of(broken));

        assertFalse(result.compiled());
        assertTrue(result.diagnosticsAsString().contains("PLM-AP-006"));
    }

    @Test
    public void processorShouldReportInvalidSimpleNameTargetsAsCompilerDiagnostics() throws Exception {
        Path broken = writeSource("com/acme/BrokenSimpleName.java", """
                package com.acme;

                import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

                @GeneratePojoLensTypedFields(simpleName = "9Broken")
                public class BrokenSimpleName {
                    public String name;
                }
                """);

        CompilationResult result = compile(List.of(broken));

        assertFalse(result.compiled());
        assertTrue(result.diagnosticsAsString().contains("PLM-AP-006"));
    }

    @Test
    public void processorShouldReportDuplicateGeneratedTargetsAsCompilerDiagnostics() throws Exception {
        Path first = writeSource("com/acme/FirstDuplicate.java", """
                package com.acme;

                import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

                @GeneratePojoLensTypedFields(packageName = "com.acme.generated", simpleName = "DuplicateFields")
                public class FirstDuplicate {
                    public String name;
                }
                """);
        Path second = writeSource("com/acme/SecondDuplicate.java", """
                package com.acme;

                import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

                @GeneratePojoLensTypedFields(packageName = "com.acme.generated", simpleName = "DuplicateFields")
                public class SecondDuplicate {
                    public String title;
                }
                """);

        CompilationResult result = compile(List.of(first, second));

        assertFalse(result.compiled());
        assertTrue(result.diagnosticsAsString().contains("PLM-AP-003"));
    }

    @Test
    public void processorShouldReportFieldGraphDepthAsCompilerDiagnostic() throws Exception {
        Path deep = writeSource("com/acme/DeepRoot.java", """
                package com.acme;

                import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

                @GeneratePojoLensTypedFields
                public class DeepRoot {
                    public Level1 level1;
                    public static class Level1 { public Level2 level2; }
                    public static class Level2 { public Level3 level3; }
                    public static class Level3 { public Level4 level4; }
                    public static class Level4 { public Level5 level5; }
                    public static class Level5 { public Level6 level6; }
                    public static class Level6 { public Level7 level7; }
                    public static class Level7 { public Level8 level8; }
                    public static class Level8 { public Level9 level9; }
                    public static class Level9 { public Level10 level10; }
                    public static class Level10 { public String name; }
                }
                """);

        CompilationResult result = compile(List.of(deep));

        assertFalse(result.compiled());
        assertTrue(result.diagnosticsAsString().contains("PLM-AP-004"));
    }

    @Test
    public void processorShouldGenerateEmptyAllForModelsWithoutEligibleFields() throws Exception {
        Path empty = writeSource("com/acme/NoEligibleFields.java", """
                package com.acme;

                import laughing.man.commits.annotations.Exclude;
                import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

                @GeneratePojoLensTypedFields
                public class NoEligibleFields {
                    public static String staticOnly;
                    public final String finalOnly = null;
                    @Exclude
                    public String excluded;
                }
                """);

        CompilationResult result = compile(List.of(empty));

        assertTrue(result.compiled(), () -> result.diagnosticsAsString());
        String source = Files.readString(tempDir.resolve("generated/com/acme/NoEligibleFieldsTypedFields.java"));
        assertTrue(source.contains("public static final List<TypedField<NoEligibleFields, ?>> ALL ="));
        assertTrue(source.contains("List.<TypedField<NoEligibleFields, ?>>of("));
        assertFalse(source.contains("STATIC_ONLY"));
        assertFalse(source.contains("FINAL_ONLY"));
        assertFalse(source.contains("EXCLUDED"));
    }

    @Test
    public void processorShouldPublishServiceAndGradleIncrementalDescriptors() throws Exception {
        Enumeration<java.net.URL> resources = PojoLensTypedFieldsProcessor.class.getClassLoader()
                .getResources("META-INF/services/javax.annotation.processing.Processor");

        boolean foundProcessorService = false;
        while (resources.hasMoreElements()) {
            String serviceFile;
            try (InputStream stream = resources.nextElement().openStream()) {
                serviceFile = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            foundProcessorService = foundProcessorService
                    || serviceFile.contains(PojoLensTypedFieldsProcessor.class.getName());
        }

        assertTrue(foundProcessorService,
                "PojoLens processor should be service-loadable for Gradle and kapt processor paths");

        try (InputStream stream = PojoLensTypedFieldsProcessor.class.getClassLoader()
                .getResourceAsStream("META-INF/gradle/incremental.annotation.processors")) {
            assertNotNull(stream, "Gradle incremental annotation processor metadata should be packaged");
            String metadata = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(metadata.lines()
                            .anyMatch(line -> line.equals(PojoLensTypedFieldsProcessor.class.getName()
                                    + ",isolating")),
                    "PojoLens processor should declare Gradle isolating incremental processing");
        }
    }

    private CompilationResult compile(List<Path> sourceFiles) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required for annotation processor tests");

        Path classes = tempDir.resolve("classes");
        Path generated = tempDir.resolve("generated");
        Files.createDirectories(classes);
        Files.createDirectories(generated);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            Boolean compiled = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of(
                            "-classpath", System.getProperty("java.class.path"),
                            "-processor", PojoLensTypedFieldsProcessor.class.getName(),
                            "-s", generated.toString(),
                            "-d", classes.toString()
                    ),
                    null,
                    units
            ).call();
            return new CompilationResult(Boolean.TRUE.equals(compiled), diagnostics.getDiagnostics());
        }
    }

    private Path writeSource(String relativePath, String source) throws IOException {
        Path path = tempDir.resolve("src").resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        return path;
    }

    private static List<String> processorFieldNames(String source) {
        return source.lines()
                .map(String::trim)
                .filter(line -> line.contains("TypedField.of("))
                .map(line -> line.substring(line.indexOf("TypedField.of(\"") + "TypedField.of(\"".length()))
                .map(line -> line.substring(0, line.indexOf('"')))
                .toList();
    }

    private static List<String> processorAllConstants(String source) {
        int allStart = source.indexOf("List.<TypedField<ParityModel, ?>>of(");
        int constructorStart = source.indexOf("    );", allStart);
        return source.substring(allStart, constructorStart)
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.contains("List.<TypedField<"))
                .map(line -> line.endsWith(",") ? line.substring(0, line.length() - 1) : line)
                .toList();
    }

    private static void assertGeneratedTypedField(String source,
                                                  String constant,
                                                  String fieldName,
                                                  String sourceType) {
        assertTrue(source.contains("TypedField<ParityModel, " + sourceType + "> " + constant),
                () -> "Expected typed declaration for " + constant);
        assertTrue(source.contains(constant + " = TypedField.of(\"" + fieldName + "\", " + sourceType + ".class);"),
                () -> "Expected TypedField.of call for " + constant);
    }

    public static class ParityModel {
        public boolean active;
        public Boolean boxedActive;
        public byte byteScore;
        public Byte boxedByteScore;
        public String collisionValue;
        public String collision_value;
        public char grade;
        public Character boxedGrade;
        public Date hireDate;
        public int id;
        public Integer boxedId;
        public LocalDate localDate;
        public LocalDateTime localDateTime;
        public long longScore;
        public Long boxedLongScore;
        public String name;
        public OffsetDateTime offsetDateTime;
        public short shortScore;
        public Short boxedShortScore;
        public Status status;
        public double total;
        public Double boxedTotal;
        public float utilization;
        public Float boxedUtilization;
        public ZonedDateTime zonedDateTime;
        public Nested nested;
        public String[] tags;
        public static String ignoredStatic;
        public final String ignoredFinal = null;
        @Exclude
        public String internal;

        public enum Status {
            ACTIVE
        }

        public static class Nested {
            public String city;
            @Exclude
            public String secret;
        }
    }

    private record CompilationResult(boolean compiled,
                                     List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        private String diagnosticsAsString() {
            StringBuilder builder = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                builder.append(diagnostic.getKind())
                        .append(": ")
                        .append(diagnostic.getMessage(null))
                        .append(System.lineSeparator());
            }
            return builder.toString();
        }
    }
}
