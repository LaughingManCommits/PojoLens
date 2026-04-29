package laughing.man.commits.metamodel;

import laughing.man.commits.util.ReflectionUtil;
import laughing.man.commits.util.StringUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates Java field-constant classes for string-based query and chart APIs.
 * Also generates typed field constant classes via {@link #generateTyped} for the typed DSL surface.
 */
public final class FieldMetamodelGenerator {

    private FieldMetamodelGenerator() {
    }

    public static FieldMetamodel generate(Class<?> modelClass) {
        Objects.requireNonNull(modelClass, "modelClass must not be null");
        return generate(modelClass, modelClass.getPackageName(), modelClass.getSimpleName() + "Fields");
    }

    public static FieldMetamodel generate(Class<?> modelClass, String packageName, String simpleName) {
        Objects.requireNonNull(modelClass, "modelClass must not be null");
        validateModelClass(modelClass);
        String normalizedPackage = normalizePackageName(packageName);
        String normalizedSimpleName = normalizeSimpleName(simpleName);

        List<String> fieldNames = collectFieldNames(modelClass);
        Map<String, String> constants = buildConstantMap(fieldNames);
        String source = renderSource(normalizedPackage, normalizedSimpleName, constants);
        return new FieldMetamodel(modelClass, normalizedPackage, normalizedSimpleName, fieldNames, constants, source);
    }

    private static void validateModelClass(Class<?> modelClass) {
        if (modelClass.isAnonymousClass() || modelClass.isLocalClass()) {
            throw new IllegalArgumentException("modelClass must be a named top-level or nested class");
        }
    }

    private static String normalizePackageName(String packageName) {
        if (StringUtil.isNullOrBlank(packageName)) {
            return "";
        }
        String normalized = packageName.trim();
        for (String segment : normalized.split("\\.")) {
            if (!isJavaIdentifier(segment)) {
                throw new IllegalArgumentException("Invalid package segment '" + segment + "'");
            }
        }
        return normalized;
    }

    private static String normalizeSimpleName(String simpleName) {
        if (StringUtil.isNullOrBlank(simpleName)) {
            throw new IllegalArgumentException("simpleName must not be null/blank");
        }
        String normalized = simpleName.trim();
        if (!isJavaIdentifier(normalized)) {
            throw new IllegalArgumentException("simpleName must be a valid Java identifier");
        }
        return normalized;
    }

    private static boolean isJavaIdentifier(String value) {
        if (value == null || value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> collectFieldNames(Class<?> modelClass) {
        ArrayList<String> fieldNames = new ArrayList<>(ReflectionUtil.collectQueryableFieldNames(modelClass));
        fieldNames.sort(String::compareTo);
        return fieldNames;
    }

    private static Map<String, String> buildConstantMap(List<String> fieldNames) {
        LinkedHashMap<String, String> constants = new LinkedHashMap<>();
        for (String fieldName : fieldNames) {
            String baseName = toConstantName(fieldName);
            String constantName = baseName;
            int suffix = 2;
            while (constants.containsKey(constantName)) {
                constantName = baseName + "_" + suffix++;
            }
            constants.put(constantName, fieldName);
        }
        return constants;
    }

    private static String toConstantName(String fieldName) {
        String normalized = fieldName
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "FIELD";
        }
        if (Character.isDigit(normalized.charAt(0))) {
            return "_" + normalized;
        }
        return normalized;
    }

    private static String renderSource(String packageName, String simpleName, Map<String, String> constants) {
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("import java.util.List;\n\n");
        source.append("public final class ").append(simpleName).append(" {\n");
        for (Map.Entry<String, String> entry : constants.entrySet()) {
            source.append("    public static final String ")
                    .append(entry.getKey())
                    .append(" = \"")
                    .append(escapeJava(entry.getValue()))
                    .append("\";\n");
        }
        source.append("\n");
        source.append("    public static final List<String> ALL = List.of(\n");
        int index = 0;
        for (String constant : constants.keySet()) {
            source.append("            ").append(constant);
            if (index < constants.size() - 1) {
                source.append(",");
            }
            source.append("\n");
            index++;
        }
        source.append("    );\n\n");
        source.append("    private ").append(simpleName).append("() {\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    // --- Typed field generation ---

    public static FieldMetamodel generateTyped(Class<?> modelClass) {
        Objects.requireNonNull(modelClass, "modelClass must not be null");
        return generateTyped(modelClass,
                modelClass.getPackageName(),
                modelClass.getSimpleName() + "TypedFields");
    }

    public static FieldMetamodel generateTyped(Class<?> modelClass, String packageName, String simpleName) {
        Objects.requireNonNull(modelClass, "modelClass must not be null");
        validateModelClass(modelClass);
        String normalizedPackage = normalizePackageName(packageName);
        String normalizedSimpleName = normalizeSimpleName(simpleName);

        List<String> fieldNames = collectFieldNames(modelClass);
        Map<String, Class<?>> fieldTypes = ReflectionUtil.collectQueryableFieldTypes(modelClass);
        Map<String, String> constants = buildConstantMap(fieldNames);
        String source = renderTypedSource(
                normalizedPackage, normalizedSimpleName,
                modelClass, normalizedPackage,
                constants, fieldTypes);
        return new FieldMetamodel(modelClass, normalizedPackage, normalizedSimpleName, fieldNames, constants, source);
    }

    private static String renderTypedSource(String packageName,
                                             String simpleName,
                                             Class<?> modelClass,
                                             String generatedPackage,
                                             Map<String, String> constants,
                                             Map<String, Class<?>> fieldTypes) {
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }

        Set<String> imports = new LinkedHashSet<>();
        imports.add("laughing.man.commits.dsl.TypedField");
        imports.add("java.util.List");
        String modelTypeName = modelClass.getSimpleName();
        String modelCanonicalName = modelClass.getCanonicalName();
        if (modelCanonicalName != null
                && (!modelClass.getPackageName().equals(generatedPackage) || modelClass.getEnclosingClass() != null)) {
            imports.add(modelCanonicalName);
        }
        for (String fieldName : constants.values()) {
            FieldTypeNames typeNames = fieldTypeNames(fieldTypes.get(fieldName));
            if (typeNames.importName() == null) {
                continue;
            }
            imports.add(typeNames.importName());
        }
        for (String imp : imports) {
            source.append("import ").append(imp).append(";\n");
        }
        source.append("\n");

        source.append("public final class ").append(simpleName).append(" {\n");
        for (Map.Entry<String, String> entry : constants.entrySet()) {
            String constantName = entry.getKey();
            String fieldName = entry.getValue();
            FieldTypeNames typeNames = fieldTypeNames(fieldTypes.get(fieldName));
            source.append("    public static final TypedField<")
                    .append(modelTypeName).append(", ").append(typeNames.sourceName()).append("> ")
                    .append(constantName)
                    .append(" = TypedField.of(\"").append(escapeJava(fieldName)).append("\", ")
                    .append(typeNames.sourceName()).append(".class);\n");
        }
        source.append("\n");
        source.append("    public static final List<TypedField<").append(modelTypeName)
                .append(", ?>> ALL =\n");
        source.append("            List.<TypedField<").append(modelTypeName).append(", ?>>of(\n");
        int index = 0;
        for (String constantName : constants.keySet()) {
            source.append("            ").append(constantName);
            if (index < constants.size() - 1) {
                source.append(",");
            }
            source.append("\n");
            index++;
        }
        source.append("    );\n\n");
        source.append("    private ").append(simpleName).append("() {\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private static FieldTypeNames fieldTypeNames(Class<?> rawType) {
        if (rawType == null) {
            return new FieldTypeNames("Object", null);
        }
        if (rawType.isArray()) {
            FieldTypeNames component = fieldTypeNames(rawType.getComponentType());
            return new FieldTypeNames(component.sourceName() + "[]", component.importName());
        }
        Class<?> boxedType = boxPrimitive(rawType);
        String importName = importName(boxedType);
        return new FieldTypeNames(boxedType.getSimpleName(), importName);
    }

    private static String importName(Class<?> type) {
        String canonicalName = type.getCanonicalName();
        if (canonicalName == null) {
            return null;
        }
        String packageName = type.getPackageName();
        if (packageName.isEmpty() || "java.lang".equals(packageName)) {
            return null;
        }
        return canonicalName;
    }

    private static Class<?> boxPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    // --- Shared helpers ---

    private static String escapeJava(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private record FieldTypeNames(String sourceName, String importName) {
    }
}

