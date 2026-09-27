package laughing.man.commits.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds record instances for query results through the canonical constructor, since
 * record components cannot be assigned after construction.
 *
 * <p>Each component takes the row value with the same name; a record-typed component is
 * built from dotted sub-fields ({@code address.city}). Missing values become {@code null}
 * (or the primitive default), and values are coerced like regular projection. A
 * {@link Plan} resolves names to row positions once per result schema, so per-row work is
 * one argument array and one constructor call.
 */
final class RecordProjectionSupport {

    private static final int CACHE_MAX_ENTRIES = 512;
    private static final Cache<Class<?>, RecordShape> SHAPES =
            Caffeine.newBuilder().maximumSize(CACHE_MAX_ENTRIES).build();

    private RecordProjectionSupport() {
    }

    /**
     * Readable backing fields of a record's components, in declaration order.
     */
    static List<Field> componentFields(Class<?> recordType) {
        return shape(recordType).fields();
    }

    /**
     * Resolves {@code recordType}'s components against a result schema (row field names).
     */
    static <T> Plan<T> compile(Class<T> recordType, List<String> schema) {
        HashMap<String, Integer> indexByName = new HashMap<>(Math.max(16, schema.size() * 2));
        for (int i = 0; i < schema.size(); i++) {
            indexByName.putIfAbsent(schema.get(i), i);
        }
        return compile(recordType, "", indexByName);
    }

    private static <T> Plan<T> compile(Class<T> recordType, String prefix, Map<String, Integer> indexByName) {
        RecordShape shape = shape(recordType);
        Slot[] slots = new Slot[shape.names().length];
        for (int i = 0; i < slots.length; i++) {
            String name = prefix + shape.names()[i];
            Class<?> type = shape.types()[i];
            Integer index = indexByName.get(name);
            Plan<?> nested = null;
            if (index == null && type.isRecord() && hasNestedNames(indexByName, name + '.')) {
                nested = compile(type, name + '.', indexByName);
            }
            slots[i] = new Slot(name, index == null ? -1 : index, nested, shape.boxedTypes()[i], shape.defaults()[i]);
        }
        return new Plan<>(recordType, shape.constructor(), slots);
    }

    private static boolean hasNestedNames(Map<String, Integer> indexByName, String prefix) {
        for (String key : indexByName.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static RecordShape shape(Class<?> recordType) {
        return SHAPES.get(recordType, RecordProjectionSupport::describe);
    }

    private static RecordShape describe(Class<?> type) {
        RecordComponent[] components = type.getRecordComponents();
        String[] names = new String[components.length];
        Class<?>[] types = new Class<?>[components.length];
        Class<?>[] boxedTypes = new Class<?>[components.length];
        Object[] defaults = new Object[components.length];
        ArrayList<Field> fields = new ArrayList<>(components.length);
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            names[i] = component.getName();
            types[i] = component.getType();
            if (types[i].isPrimitive()) {
                defaults[i] = Array.get(Array.newInstance(types[i], 1), 0);
                boxedTypes[i] = defaults[i].getClass();
            } else {
                boxedTypes[i] = types[i];
            }
            try {
                Field field = type.getDeclaredField(component.getName());
                field.setAccessible(true);
                fields.add(field);
            } catch (NoSuchFieldException impossible) {
                throw new IllegalStateException("Record component without backing field: " + component, impossible);
            }
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return new RecordShape(names, types, boxedTypes, defaults, List.copyOf(fields), constructor);
        } catch (NoSuchMethodException impossible) {
            throw new IllegalStateException("Record without canonical constructor: " + type, impossible);
        }
    }

    /**
     * Supplies the value at a result-schema position for one row.
     */
    @FunctionalInterface
    interface RowValues {
        Object valueAt(int index);
    }

    static final class Plan<T> {
        private final Class<T> recordType;
        private final Constructor<?> constructor;
        private final Slot[] slots;

        private Plan(Class<T> recordType, Constructor<?> constructor, Slot[] slots) {
            this.recordType = recordType;
            this.constructor = constructor;
            this.slots = slots;
        }

        T construct(RowValues row) throws ReflectiveOperationException {
            Object[] arguments = new Object[slots.length];
            for (int i = 0; i < slots.length; i++) {
                Slot slot = slots[i];
                if (slot.sourceIndex() >= 0) {
                    arguments[i] = slot.coerce(row.valueAt(slot.sourceIndex()));
                } else if (slot.nested() != null) {
                    arguments[i] = slot.nested().construct(row);
                } else {
                    arguments[i] = slot.defaultValue();
                }
            }
            return recordType.cast(constructor.newInstance(arguments));
        }
    }

    private record Slot(String name, int sourceIndex, Plan<?> nested, Class<?> boxedType, Object defaultValue) {

        private Object coerce(Object value) {
            if (value == null) {
                return defaultValue;
            }
            if (boxedType.isInstance(value)) {
                return value;
            }
            Object converted = ObjectUtil.castValue(value, boxedType);
            if (converted == null) {
                throw new IllegalArgumentException("Cannot convert value for record component '" + name
                        + "' to " + boxedType.getSimpleName() + ": " + value);
            }
            return converted;
        }
    }

    private record RecordShape(String[] names,
                               Class<?>[] types,
                               Class<?>[] boxedTypes,
                               Object[] defaults,
                               List<Field> fields,
                               Constructor<?> constructor) {
    }
}
