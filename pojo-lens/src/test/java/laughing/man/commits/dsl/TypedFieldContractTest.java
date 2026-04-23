package laughing.man.commits.dsl;

import laughing.man.commits.metamodel.FieldMetamodel;
import laughing.man.commits.metamodel.FieldMetamodelGenerator;
import laughing.man.commits.testutil.BusinessFixtures.Employee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TypedFieldContractTest {

    // --- TypedField public API contract ---

    @Test
    void stableTypedFieldContractShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(TypedField.class, "of", String.class, Class.class);
        requirePublicMethod(TypedField.class, "fieldName");
        requirePublicMethod(TypedField.class, "valueType");
        requirePublicMethod(TypedField.class, "toString");
        requirePublicMethod(TypedField.class, "equals", Object.class);
        requirePublicMethod(TypedField.class, "hashCode");
    }

    @Test
    void stableTypedMetamodelGeneratorContractShouldRemainAvailable() throws Exception {
        requirePublicStaticMethod(FieldMetamodelGenerator.class, "generateTyped", Class.class);
        requirePublicStaticMethod(FieldMetamodelGenerator.class, "generateTyped",
                Class.class, String.class, String.class);
    }

    // --- TypedField behavior ---

    @Test
    void ofCreatesTypedFieldWithCorrectFieldNameAndValueType() {
        TypedField<Employee, String> field = TypedField.of("name", String.class);
        assertEquals("name", field.fieldName());
        assertEquals(String.class, field.valueType());
    }

    @Test
    void toStringReturnsFieldName() {
        assertEquals("salary", TypedField.of("salary", Integer.class).toString());
    }

    @Test
    void equalityIsValueBased() {
        TypedField<Employee, String> a = TypedField.of("name", String.class);
        TypedField<Employee, String> b = TypedField.of("name", String.class);
        TypedField<Employee, Integer> c = TypedField.of("age", Integer.class);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void nullFieldNameThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> TypedField.of(null, String.class));
    }

    @Test
    void blankFieldNameThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TypedField.of("", String.class));
        assertThrows(IllegalArgumentException.class, () -> TypedField.of("  ", String.class));
    }

    @Test
    void nullValueTypeThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> TypedField.of("name", null));
    }

    @Test
    void dottedPathFieldNameIsAccepted() {
        TypedField<Object, String> field = TypedField.of("address.city", String.class);
        assertEquals("address.city", field.fieldName());
    }

    // --- generateTyped behavior ---

    @Test
    void generateTypedProducesSourceWithTypedFieldDeclarations() {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generateTyped(Employee.class);

        assertNotNull(metamodel);
        assertEquals(Employee.class, metamodel.modelClass());
        assertTrue(metamodel.simpleName().endsWith("TypedFields"));
        assertFalse(metamodel.fieldNames().isEmpty());

        String source = metamodel.source();
        assertTrue(source.contains("import laughing.man.commits.dsl.TypedField;"),
                "source must import TypedField");
        assertTrue(source.contains("TypedField.of("), "source must call TypedField.of");
        assertTrue(source.contains("List.<TypedField<"), "source must include typed ALL list");
    }

    @Test
    void generateTypedFieldNamesMatchStringGeneratorFieldNames() {
        FieldMetamodel stringModel = FieldMetamodelGenerator.generate(Employee.class);
        FieldMetamodel typedModel = FieldMetamodelGenerator.generateTyped(Employee.class);

        assertEquals(stringModel.fieldNames(), typedModel.fieldNames(),
                "typed generator must cover the same fields as string generator");
    }

    @Test
    void generateTypedWithCustomPackageAndSimpleName() {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generateTyped(
                Employee.class, "com.example", "EmployeeTypedFields");

        assertEquals("com.example", metamodel.packageName());
        assertEquals("EmployeeTypedFields", metamodel.simpleName());
        assertTrue(metamodel.source().startsWith("package com.example;"));
    }

    @Test
    void generateTypedSourceIncludesNonJavaLangImports() {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generateTyped(Employee.class);
        // Employee has a Date field — java.util.Date must be imported
        assertTrue(metamodel.source().contains("import java.util.Date;"),
                "source must import java.util.Date for hireDate field");
    }

    @Test
    void generateTypedSourceUsesBoxedPrimitiveFieldTypes() {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generateTyped(Employee.class);

        assertTrue(metamodel.source().contains("TypedField<Employee, Integer> ID"),
                "primitive int id field must be generated as Integer");
        assertTrue(metamodel.source().contains("TypedField<Employee, Boolean> ACTIVE"),
                "primitive boolean active field must be generated as Boolean");
        assertFalse(metamodel.source().contains("TypedField<Employee, int>"));
        assertFalse(metamodel.source().contains("TypedField<Employee, boolean>"));
    }

    @Test
    void generateTypedSourceForNestedModelCompiles(@TempDir Path tempDir) throws Exception {
        FieldMetamodel metamodel = FieldMetamodelGenerator.generateTyped(
                Employee.class,
                "laughing.man.commits.generated",
                "EmployeeTypedFields");

        Path sourceRoot = tempDir.resolve("src");
        Path classesRoot = tempDir.resolve("classes");
        Path javaFile = metamodel.writeTo(sourceRoot);

        compile(javaFile, classesRoot);
    }

    // --- helpers ---

    private static Method requirePublicMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method m = type.getMethod(name, params);
        assertTrue(Modifier.isPublic(m.getModifiers()),
                () -> "Expected public method: " + type.getSimpleName() + "." + name);
        return m;
    }

    private static Method requirePublicStaticMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method m = requirePublicMethod(type, name, params);
        assertTrue(Modifier.isStatic(m.getModifiers()),
                () -> "Expected static method: " + type.getSimpleName() + "." + name);
        return m;
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
                    List.of(
                            "-classpath", System.getProperty("java.class.path"),
                            "-d", classesRoot.toString()),
                    null,
                    units
            ).call();
            assertTrue(compiled, "Generated typed metamodel source should compile");
        }
    }
}
