package laughing.man.commits.metamodel;

import laughing.man.commits.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Generates Java field-constant classes for string-based query and chart APIs.
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
        if (packageName == null || packageName.isBlank()) {
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
        if (simpleName == null || simpleName.isBlank()) {
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

    private static String escapeJava(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}

