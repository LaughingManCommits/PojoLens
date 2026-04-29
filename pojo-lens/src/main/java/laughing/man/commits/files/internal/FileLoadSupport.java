package laughing.man.commits.files.internal;

import laughing.man.commits.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared reflection-backed row-schema support for file-boundary loaders.
 */
public final class FileLoadSupport {

    private FileLoadSupport() {
    }

    public static RowSchema rowSchema(Class<?> rowType) {
        if (rowType == null) {
            throw new IllegalArgumentException("rowType must not be null");
        }
        LinkedHashSet<String> fieldNames = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(rowType));
        ArrayList<String> orderedFieldNames = new ArrayList<>(fieldNames.size());
        LinkedHashSet<String> primitiveFieldNames = new LinkedHashSet<>();
        for (String fieldName : fieldNames) {
            FieldBinding binding = resolveFieldBinding(rowType, fieldName);
            if (binding == null) {
                continue;
            }
            orderedFieldNames.add(binding.fieldName());
            if (binding.primitive()) {
                primitiveFieldNames.add(binding.fieldName());
            }
        }
        return new RowSchema(orderedFieldNames, primitiveFieldNames);
    }

    public static FieldBinding resolveFieldBinding(Class<?> rowType, String fieldName) {
        if (rowType == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        String[] parts = fieldName.split("\\.");
        Class<?> currentType = rowType;
        Class<?> wrappedLeafType = null;
        boolean primitive = false;

        for (String part : parts) {
            Field field = findField(currentType, part);
            if (field == null) {
                return null;
            }
            primitive = field.getType().isPrimitive();
            wrappedLeafType = wrapPrimitive(field.getType());
            currentType = wrappedLeafType;
        }

        if (wrappedLeafType == null) {
            return null;
        }
        return new FieldBinding(fieldName, wrappedLeafType, primitive);
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
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
        if (type == boolean.class) {
            return Boolean.class;
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
        return type;
    }

    public record RowSchema(List<String> fieldNames, Set<String> primitiveFieldNames) {
        public RowSchema {
            fieldNames = List.copyOf(fieldNames == null ? List.of() : fieldNames);
            primitiveFieldNames = Set.copyOf(primitiveFieldNames == null ? Set.of() : primitiveFieldNames);
        }
    }

    public record FieldBinding(String fieldName, Class<?> valueType, boolean primitive) {
    }
}
