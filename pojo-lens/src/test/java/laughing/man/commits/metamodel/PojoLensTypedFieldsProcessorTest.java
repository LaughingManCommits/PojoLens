package laughing.man.commits.metamodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PojoLensTypedFieldsProcessorTest {

    @TempDir
    Path tempDir;

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
    public void processorShouldReportInvalidTargetsAsCompilerDiagnostics() throws Exception {
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
