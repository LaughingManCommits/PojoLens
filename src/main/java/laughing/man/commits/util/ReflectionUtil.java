package laughing.man.commits.util;

import laughing.man.commits.annotations.Exclude;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.QueryField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.*;

public class ReflectionUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionUtil.class);
    private static final int MAX_FIELD_GRAPH_DEPTH = 8;
    private static final Map<Class<?>, List<Field>> MUTABLE_FIELD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> MUTABLE_FIELD_BY_NAME_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, FieldGraphDescriptor> FIELD_GRAPH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<FieldPathCacheKey, ResolvedFieldPath> FIELD_PATH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ResolvedFieldPath MISSING_FIELD_PATH = new ResolvedFieldPath(List.of(), null, false);

    /**
     * Converts internal domain rows back to typed objects.
     */
    public static <T> List<T> toClassList(Class<T> cls,
                                          List<QueryRow> classes) {
        List<T> result = new ArrayList<>();
        if (classes == null || classes.isEmpty()) {
            return result;
        }
        try {
            Map<String, ResolvedFieldPath> resolvedFieldsByName = new HashMap<>();
            for (QueryRow row : classes) {
                T object = cls.getDeclaredConstructor().newInstance();
                List<? extends QueryField> fields = row.getFields();
                for (QueryField field : fields) {
                    if (field == null || field.getFieldName() == null || field.getValue() == null) {
                        continue;
                    }
                    ResolvedFieldPath resolvedField = resolvedFieldsByName.computeIfAbsent(
                            field.getFieldName(),
                            fieldName -> resolveFieldPath(cls, fieldName)
                    );
                    if (!resolvedField.resolvable() || resolvedField.leafType() == null) {
                        continue;
                    }
                    Object value = ObjectUtil.castValue(field.getValue(), resolvedField.leafType());
                    setResolvedFieldValue(object, resolvedField, value, field.getFieldName());
                }
                result.add(object);
            }
        } catch (Exception e) {
            LOG.error("Failed to Convert Objects [" + cls.getSimpleName() + "] to new List", e);
            throw new IllegalStateException("Failed to convert domain rows to " + cls.getSimpleName(), e);
        }
        return result;
    }

    /**
     * Sets a mutable field value by name.
     */
    public static void setFieldValue(Object javaBean, String propertyName,
            Object propertyValue) throws Exception {
        try {
            if (javaBean == null || propertyName == null || propertyName.isBlank()) {
                return;
            }
            ResolvedFieldPath fieldPath = resolveFieldPath(javaBean.getClass(), propertyName);
            if (!fieldPath.resolvable()) {
                return;
            }
            setResolvedFieldValue(javaBean, fieldPath, propertyValue, propertyName);
        } catch (IllegalAccessException
                | IllegalArgumentException
                | SecurityException e) {
            LOG.error("Failed to set Property [" + propertyName + "], "
                    + "Parms [" + propertyValue + "]", e);
            throw e;
        }
    }

    /**
     * Flattens input beans into internal domain rows used by the query engine.
     */
    public static List<QueryRow> toDomainRows(List<?> conversionList) {
        List<QueryRow> domainRows = new ArrayList<>();
        if (conversionList == null || conversionList.isEmpty()) {
            return domainRows;
        }

        Object firstBean = null;
        for (Object bean : conversionList) {
            if (bean != null) {
                firstBean = bean;
                break;
            }
        }
        if (firstBean == null) {
            return domainRows;
        }

        List<String> fields = collectQueryableFieldNames(firstBean.getClass());

        for (Object currentBean : conversionList) {
            if (currentBean == null) {
                continue;
            }
            HashMap<String, Object> fieldValuesByName = new HashMap<>();
            collectFieldValueMap(currentBean, fieldValuesByName, "", Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            String rowId = newUUID();
            QueryRow domainRow = new QueryRow();
            domainRow.setRowId(rowId);
            List<QueryField> fieldList = new ArrayList<>();
            fields.stream().map((fieldName) -> {
                Object value = null;
                if (fieldValuesByName.containsKey(fieldName)) {
                    value = fieldValuesByName.get(fieldName);
                }
                QueryField field = new QueryField();
                field.setFieldName(fieldName);
                field.setValue(value);
                return field;
            }).forEachOrdered(fieldList::add);
            domainRow.setFields(fieldList);
            domainRows.add(domainRow);
        }

        return domainRows;
    }

    /**
     * Recursively extracts simple field values from object graphs.
     */
    private static void collectFieldValueMap(Object bean,
                                             Map<String, Object> objectValueMap,
                                             String prefix,
                                             Set<Object> activePath,
                                             int depth) {
        if (bean == null) {
            return;
        }
        if (depth > MAX_FIELD_GRAPH_DEPTH) {
            throw new IllegalStateException("Field graph depth exceeds max depth of " + MAX_FIELD_GRAPH_DEPTH);
        }
        if (!activePath.add(bean)) {
            return;
        }
        try {
            for (Field field : getMutableFields(bean.getClass())) {
                if (field.isAnnotationPresent(Exclude.class)) {
                    continue;
                }
                String fieldName = qualify(prefix, field.getName());
                Class<?> fieldType = wrapPrimitive(field.getType());
                Object fieldValue = field.get(bean);
                if (isSimpleType(fieldType) || isSimpleValue(fieldValue)) {
                    objectValueMap.put(fieldName, fieldValue);
                } else if (fieldValue != null && isTraversableType(fieldValue.getClass())) {
                    collectFieldValueMap(fieldValue, objectValueMap, fieldName, activePath, depth + 1);
                }
            }
        } catch (SecurityException
                | IllegalArgumentException
                | IllegalAccessException e) {
            LOG.error("Failed to Convert Objects [" + bean.getClass().getSimpleName() + "] to new List", e);
            throw new IllegalStateException("Failed to flatten object fields", e);
        } finally {
            activePath.remove(bean);
        }
    }

    /**
     * Generates internal unique identifiers used by query rules and rows.
     */
    public static String newUUID() {
        UUID uniqueString = UUID.randomUUID();
        String unchangedString = uniqueString.toString();
        StringBuilder uniqueID = new StringBuilder();
        for (int x = 0; x < unchangedString.length(); x++) {
            String singleChar = unchangedString.substring(x, x + 1);
            if (singleChar.matches("^\\d+$")) {
                int number = Integer.parseInt(singleChar);
                char c = (char) (number + 97);
                uniqueID.append(singleChar.replaceAll("^\\d+$", String.valueOf(c)));
            } else {
                uniqueID.append(singleChar);
            }
        }
        return uniqueID.toString().replaceAll("-", "_");
    }

    /**
     * Reads a mutable field value by name.
     */
    public static Object getFieldValue(Object cls, String fieldName) throws Exception {
        Object value = null;
        try {
            if (cls == null || fieldName == null || fieldName.isBlank()) {
                return null;
            }
            ResolvedFieldPath fieldPath = resolveFieldPath(cls.getClass(), fieldName);
            if (!fieldPath.resolvable()) {
                return null;
            }
            Object current = cls;
            for (Field field : fieldPath.fields()) {
                if (current == null) {
                    return null;
                }
                value = field.get(current);
                current = value;
            }
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
            LOG.error("Failed to get Property [" + cls + "]", e);
            throw e;
        }
        return value;
    }

    public static List<String> collectQueryableFieldNames(Class<?> root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        return fieldGraph(root).fieldNames();
    }

    public static Map<String, Class<?>> collectQueryableFieldTypes(Class<?> root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        return fieldGraph(root).fieldTypes();
    }

    private static List<Field> getMutableFields(Class<?> clazz) {
        return MUTABLE_FIELD_CACHE.computeIfAbsent(clazz, ReflectionUtil::getFields);
    }

    public static List<Field> getFields(Class<?> key) {
        List<Field> fields = new ArrayList<>();
        for (Field field : key.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isFinal(mods) && !Modifier.isStatic(mods)) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    private static Field findMutableField(Class<?> clazz, String fieldName) {
        return MUTABLE_FIELD_BY_NAME_CACHE
                .computeIfAbsent(clazz, ReflectionUtil::buildMutableFieldByNameMap)
                .get(fieldName);
    }

    private static Map<String, Field> buildMutableFieldByNameMap(Class<?> clazz) {
        Map<String, Field> byName = new HashMap<>();
        for (Field field : getMutableFields(clazz)) {
            byName.put(field.getName(), field);
        }
        return byName;
    }

    private static boolean isSimpleValue(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof String
                || value instanceof Date;
    }

    public static boolean isSimpleType(Class<?> type) {
        Class<?> wrapped = wrapPrimitive(type);
        return wrapped != null
                && (wrapped.equals(Integer.class)
                || wrapped.equals(Long.class)
                || wrapped.equals(Double.class)
                || wrapped.equals(Float.class)
                || wrapped.equals(Boolean.class)
                || wrapped.equals(Short.class)
                || wrapped.equals(Byte.class)
                || wrapped.equals(Character.class)
                || wrapped.equals(String.class)
                || wrapped.equals(Date.class));
    }

    private static boolean isPlatformType(Class<?> type) {
        Package pkg = type.getPackage();
        if (pkg == null) {
            return true;
        }
        String name = pkg.getName();
        return !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("jdk.");
    }

    private static boolean isTraversableType(Class<?> type) {
        return type != null && !isSimpleType(type) && !type.isEnum() && isPlatformType(type);
    }

    private static FieldGraphDescriptor fieldGraph(Class<?> root) {
        return FIELD_GRAPH_CACHE.computeIfAbsent(root, ReflectionUtil::buildFieldGraphDescriptor);
    }

    private static FieldGraphDescriptor buildFieldGraphDescriptor(Class<?> root) {
        LinkedHashMap<String, Class<?>> fieldTypes = new LinkedHashMap<>();
        collectFieldGraph(root, "", new LinkedHashSet<>(), 0, fieldTypes);
        return new FieldGraphDescriptor(new ArrayList<>(fieldTypes.keySet()), fieldTypes);
    }

    private static void collectFieldGraph(Class<?> type,
                                          String prefix,
                                          Set<Class<?>> activePath,
                                          int depth,
                                          Map<String, Class<?>> fieldTypes) {
        if (type == null) {
            return;
        }
        if (depth > MAX_FIELD_GRAPH_DEPTH) {
            throw new IllegalArgumentException("Field graph depth exceeds max depth of " + MAX_FIELD_GRAPH_DEPTH);
        }
        if (isSimpleType(type) || type.isEnum() || !isTraversableType(type)) {
            return;
        }
        if (!activePath.add(type)) {
            return;
        }
        for (Field field : getMutableFields(type)) {
            if (field.isAnnotationPresent(Exclude.class)) {
                continue;
            }
            String qualifiedName = qualify(prefix, field.getName());
            Class<?> fieldType = wrapPrimitive(field.getType());
            if (isSimpleType(fieldType)) {
                fieldTypes.putIfAbsent(qualifiedName, fieldType);
                continue;
            }
            if (isTraversableType(fieldType)) {
                collectFieldGraph(fieldType, qualifiedName, activePath, depth + 1, fieldTypes);
            }
        }
        activePath.remove(type);
    }

    private static ResolvedFieldPath resolveFieldPath(Class<?> rootType, String fieldName) {
        if (rootType == null || fieldName == null || fieldName.isBlank()) {
            return MISSING_FIELD_PATH;
        }
        return FIELD_PATH_CACHE.computeIfAbsent(
                new FieldPathCacheKey(rootType, fieldName),
                key -> buildResolvedFieldPath(key.rootType(), key.fieldName())
        );
    }

    private static ResolvedFieldPath buildResolvedFieldPath(Class<?> rootType, String fieldName) {
        String[] parts = fieldName.split("\\.");
        ArrayList<Field> fields = new ArrayList<>(parts.length);
        Class<?> currentType = rootType;
        for (String part : parts) {
            if (part.isBlank()) {
                return MISSING_FIELD_PATH;
            }
            Field field = findMutableField(currentType, part);
            if (field == null) {
                return MISSING_FIELD_PATH;
            }
            fields.add(field);
            currentType = field.getType();
        }
        if (fields.isEmpty()) {
            return MISSING_FIELD_PATH;
        }
        Field leaf = fields.get(fields.size() - 1);
        return new ResolvedFieldPath(fields, wrapPrimitive(leaf.getType()), true);
    }

    private static void setResolvedFieldValue(Object javaBean,
                                              ResolvedFieldPath fieldPath,
                                              Object propertyValue,
                                              String propertyName) throws Exception {
        Object current = javaBean;
        List<Field> fields = fieldPath.fields();
        for (int i = 0; i < fields.size() - 1; i++) {
            Field field = fields.get(i);
            Object nested = field.get(current);
            if (nested == null) {
                if (propertyValue == null) {
                    return;
                }
                nested = instantiateNestedValue(field.getType(), propertyName);
                field.set(current, nested);
            }
            current = nested;
        }
        Field field = fields.get(fields.size() - 1);
        if (field != null) {
            field.set(current, propertyValue);
        }
    }

    private static Object instantiateNestedValue(Class<?> fieldType, String propertyName) throws Exception {
        if (fieldType == null || isSimpleType(fieldType) || fieldType.isEnum() || !isTraversableType(fieldType)) {
            throw new IllegalArgumentException("Cannot materialize nested path '" + propertyName + "'");
        }
        try {
            java.lang.reflect.Constructor<?> constructor = fieldType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot materialize nested path '" + propertyName + "'", ex);
        }
    }

    private static String qualify(String prefix, String fieldName) {
        if (prefix == null || prefix.isEmpty()) {
            return fieldName;
        }
        return prefix + "." + fieldName;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type.equals(int.class)) {
            return Integer.class;
        }
        if (type.equals(long.class)) {
            return Long.class;
        }
        if (type.equals(double.class)) {
            return Double.class;
        }
        if (type.equals(float.class)) {
            return Float.class;
        }
        if (type.equals(boolean.class)) {
            return Boolean.class;
        }
        if (type.equals(short.class)) {
            return Short.class;
        }
        if (type.equals(byte.class)) {
            return Byte.class;
        }
        if (type.equals(char.class)) {
            return Character.class;
        }
        return type;
    }

    private record FieldPathCacheKey(Class<?> rootType, String fieldName) {
    }

    private record ResolvedFieldPath(List<Field> fields, Class<?> leafType, boolean resolvable) {
        private ResolvedFieldPath(List<Field> fields, Class<?> leafType, boolean resolvable) {
            this.fields = List.copyOf(fields);
            this.leafType = leafType;
            this.resolvable = resolvable;
        }
    }

    private record FieldGraphDescriptor(List<String> fieldNames, Map<String, Class<?>> fieldTypes) {
        private FieldGraphDescriptor(List<String> fieldNames, Map<String, Class<?>> fieldTypes) {
            this.fieldNames = List.copyOf(fieldNames);
            this.fieldTypes = Collections.unmodifiableMap(new LinkedHashMap<>(fieldTypes));
        }
    }

    private ReflectionUtil() {
    }

}

