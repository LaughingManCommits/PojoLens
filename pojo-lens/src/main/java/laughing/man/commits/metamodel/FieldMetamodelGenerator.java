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
                modelClass.getSimpleName(), modelClass.getPackageName(), normalizedPackage,
                constants, fieldTypes);
        return new FieldMetamodel(modelClass, normalizedPackage, normalizedSimpleName, fieldNames, constants, source);
    }

    private static String renderTypedSource(String packageName,
                                             String simpleName,
                                             String modelSimpleName,
                                             String modelPackage,
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
        if (!modelPackage.isEmpty() && !modelPackage.equals(generatedPackage)) {
            imports.add(modelPackage + "." + modelSimpleName);
        }
        for (String fieldName : constants.values()) {
            Class<?> vt = fieldTypes.get(fieldName);
            if (vt == null) {
                continue;
            }
            String pkg = vt.getPackageName();
            if (!pkg.isEmpty() && !pkg.equals("java.lang")) {
                String canonical = vt.getCanonicalName();
                if (canonical != null) {
                    imports.add(canonical);
                }
            }
        }
        for (String imp : imports) {
            source.append("import ").append(imp).append(";\n");
        }
        source.append("\n");

        source.append("public final class ").append(simpleName).append(" {\n");
        for (Map.Entry<String, String> entry : constants.entrySet()) {
            String constantName = entry.getKey();
            String fieldName = entry.getValue();
            Class<?> vt = fieldTypes.get(fieldName);
            String vtName = vt != null ? vt.getSimpleName() : "Object";
            source.append("    public static final TypedField<")
                    .append(modelSimpleName).append(", ").append(vtName).append("> ")
                    .append(constantName)
                    .append(" = TypedField.of(\"").append(escapeJava(fieldName)).append("\", ")
                    .append(vtName).append(".class);\n");
        }
        source.append("\n");
        source.append("    public static final List<TypedField<").append(modelSimpleName)
                .append(", ?>> ALL =\n");
        source.append("            List.<TypedField<").append(modelSimpleName).append(", ?>>of(\n");
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

    // --- Shared helpers ---

    private static String escapeJava(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}

