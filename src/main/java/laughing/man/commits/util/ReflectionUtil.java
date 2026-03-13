package laughing.man.commits.util;

import laughing.man.commits.annotations.Exclude;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectionUtil.class);

    private static final int MAX_FIELD_GRAPH_DEPTH = 8;

    private static final Map<Class<?>, List<Field>> MUTABLE_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> MUTABLE_FIELD_BY_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, FieldGraphDescriptor> FIELD_GRAPH_CACHE = new ConcurrentHashMap<>();
    private static final Map<FieldPathCacheKey, ResolvedFieldPath> FIELD_PATH_CACHE = new ConcurrentHashMap<>();
    private static final Map<ProjectionPlanCacheKey, ProjectionWritePlan> PROJECTION_WRITE_PLAN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Constructor<?>> NO_ARG_CTOR_CACHE = new ConcurrentHashMap<>();

    private static final ResolvedFieldPath MISSING_FIELD_PATH = new ResolvedFieldPath(List.of(), null, false);

    private static final char[] ALPHA_HEX = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p'
    };

    private ReflectionUtil() {
    }

    /**
     * Converts internal domain rows back to typed objects.
     */
    public static <T> List<T> toClassList(Class<T> cls, List<QueryRow> classes) {
        if (classes == null || classes.isEmpty()) {
            return new ArrayList<>(0);
        }

        List<T> result = new ArrayList<>(classes.size());

        try {
            ProjectionWritePlan plan = projectionWritePlan(cls, classes);

            for (int rowIndex = 0; rowIndex < classes.size(); rowIndex++) {
                QueryRow row = classes.get(rowIndex);
                if (row == null) {
                    continue;
                }

                T object = instantiateNoArg(cls);
                List<? extends QueryField> fields = row.getFields();
                if (fields == null || fields.isEmpty() || plan.steps().isEmpty()) {
                    result.add(object);
                    continue;
                }

                applyProjectionWritePlan(object, fields, plan);

                result.add(object);
            }
        } catch (Exception e) {
            LOG.error("Failed to Convert Objects [{}] to new List", cls.getSimpleName(), e);
            throw new IllegalStateException("Failed to convert domain rows to " + cls.getSimpleName(), e);
        }

        return result;
    }

    /**
     * Sets a mutable field value by name.
     */
    public static void setFieldValue(Object javaBean, String propertyName, Object propertyValue) throws Exception {
        try {
            if (javaBean == null || propertyName == null || propertyName.isBlank()) {
                return;
            }

            ResolvedFieldPath fieldPath = resolveFieldPath(javaBean.getClass(), propertyName);
            if (!fieldPath.resolvable()) {
                return;
            }

            setResolvedFieldValue(javaBean, fieldPath, propertyValue, propertyName);
        } catch (IllegalAccessException | IllegalArgumentException | SecurityException e) {
            LOG.error("Failed to set Property [{}], Parms [{}]", propertyName, propertyValue, e);
            throw e;
        }
    }

    public static <T> List<T> toClassList(Class<T> cls,
                                          List<Object[]> rows,
                                          List<String> sourceFieldSchema) {
        return toClassList(cls, rows, sourceFieldSchema, null);
    }

    public static <T> List<T> toClassList(Class<T> cls,
                                          List<Object[]> rows,
                                          List<String> sourceFieldSchema,
                                          int[] sourceIndexes) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>(0);
        }

        List<T> result = new ArrayList<>(rows.size());

        try {
            ProjectionWritePlan plan = projectionWritePlanForSchema(cls, sourceFieldSchema);

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Object[] row = rows.get(rowIndex);
                if (row == null) {
                    continue;
                }

                T object = instantiateNoArg(cls);
                if (row.length == 0 || plan.steps().isEmpty()) {
                    result.add(object);
                    continue;
                }

                applyProjectionWritePlan(object, row, sourceIndexes, plan);
                result.add(object);
            }
        } catch (Exception e) {
            LOG.error("Failed to Convert Objects [{}] to new List", cls.getSimpleName(), e);
            throw new IllegalStateException("Failed to convert array rows to " + cls.getSimpleName(), e);
        }

        return result;
    }

    /**
     * Flattens input beans into internal domain rows used by the query engine.
     */
    public static List<QueryRow> toDomainRows(List<?> conversionList) {
        return toDomainRows(conversionList, null);
    }

    /**
     * Flattens input beans into internal domain rows used by the query engine.
     * When {@code selectedFieldNames} is provided, only those flattened fields
     * are materialized, in their original schema order.
     */
    public static List<QueryRow> toDomainRows(List<?> conversionList, Collection<String> selectedFieldNames) {
        if (conversionList == null || conversionList.isEmpty()) {
            return new ArrayList<>(0);
        }

        Object firstBean = null;
        for (int i = 0; i < conversionList.size(); i++) {
            Object bean = conversionList.get(i);
            if (bean != null) {
                firstBean = bean;
                break;
            }
        }

        if (firstBean == null) {
            return new ArrayList<>(0);
        }

        FieldGraphDescriptor descriptor = fieldGraph(firstBean.getClass());
        List<FlattenedFieldDescriptor> flattenedFields = selectedFlattenedFields(descriptor, selectedFieldNames);
        List<QueryRow> domainRows = new ArrayList<>(conversionList.size());

        for (int i = 0; i < conversionList.size(); i++) {
            Object currentBean = conversionList.get(i);
            if (currentBean == null) {
                continue;
            }

            try {
                QueryRow domainRow = new QueryRow();
                domainRow.setFields(extractQueryFields(currentBean, flattenedFields));
                domainRows.add(domainRow);
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
                LOG.error("Failed to Convert Objects [{}] to new List", currentBean.getClass().getSimpleName(), e);
                throw new IllegalStateException("Failed to flatten object fields", e);
            }
        }

        return domainRows;
    }

    /**
     * Generates internal unique identifiers used by query rules and rows.
     */
    public static String newUUID() {
        UUID uuid = UUID.randomUUID();
        char[] out = new char[36];

        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        writeAlphaHex(out, 0, msb >>> 32, 8);
        out[8] = '_';
        writeAlphaHex(out, 9, msb >>> 16, 4);
        out[13] = '_';
        writeAlphaHex(out, 14, msb, 4);
        out[18] = '_';
        writeAlphaHex(out, 19, lsb >>> 48, 4);
        out[23] = '_';
        writeAlphaHex(out, 24, lsb, 12);

        return new String(out);
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
            List<Field> fields = fieldPath.fields();

            for (int i = 0; i < fields.size(); i++) {
                if (current == null) {
                    return null;
                }

                Field field = fields.get(i);
                value = field.get(current);
                current = value;
            }
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
            LOG.error("Failed to get Property [{}]", cls, e);
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

    public static Map<String, Class<?>> collectQueryRowFieldTypes(List<QueryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Class<?>> fieldTypes = new LinkedHashMap<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            QueryRow row = rows.get(rowIndex);
            if (row == null || row.getFields() == null) {
                continue;
            }
            List<? extends QueryField> fields = row.getFields();
            for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
                QueryField field = fields.get(fieldIndex);
                if (field == null || field.getFieldName() == null || field.getFieldName().isBlank()) {
                    continue;
                }
                fieldTypes.putIfAbsent(field.getFieldName(), null);
                if (fieldTypes.get(field.getFieldName()) == null && field.getValue() != null) {
                    fieldTypes.put(field.getFieldName(), field.getValue().getClass());
                }
            }
        }
        return Collections.unmodifiableMap(fieldTypes);
    }

    public static FlatRowReadPlan compileFlatRowReadPlan(Class<?> root, Collection<String> selectedFieldNames) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        FieldGraphDescriptor descriptor = fieldGraph(root);
        List<FlattenedFieldDescriptor> flattenedFields = selectedFlattenedFields(descriptor, selectedFieldNames);
        return new FlatRowReadPlan(flattenedFields);
    }

    public static Object[] readFlatRowValues(Object bean, FlatRowReadPlan plan) {
        if (bean == null || plan == null) {
            return new Object[0];
        }
        try {
            Object[] values = new Object[plan.size()];
            List<FlattenedFieldDescriptor> flattenedFields = plan.flattenedFields();
            for (int i = 0; i < flattenedFields.size(); i++) {
                values[i] = readResolvedFieldValue(bean, flattenedFields.get(i).fieldPath());
            }
            return values;
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
            LOG.error("Failed to read flattened row values [{}]", bean.getClass().getSimpleName(), e);
            throw new IllegalStateException("Failed to flatten object fields", e);
        }
    }

    private static List<Field> getMutableFields(Class<?> clazz) {
        return MUTABLE_FIELD_CACHE.computeIfAbsent(clazz, ReflectionUtil::getFields);
    }

    public static List<Field> getFields(Class<?> key) {
        Field[] declaredFields = key.getDeclaredFields();
        List<Field> fields = new ArrayList<>(declaredFields.length);

        for (int i = 0; i < declaredFields.length; i++) {
            Field field = declaredFields[i];
            int mods = field.getModifiers();

            if (!Modifier.isFinal(mods)
                    && !Modifier.isStatic(mods)
                    && !field.isAnnotationPresent(Exclude.class)) {
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
        List<Field> fields = getMutableFields(clazz);
        Map<String, Field> byName = new HashMap<>(Math.max(16, fields.size() * 2));

        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            byName.put(field.getName(), field);
        }

        return byName;
    }

    public static boolean isSimpleType(Class<?> type) {
        Class<?> wrapped = wrapPrimitive(type);
        return wrapped == Integer.class
                || wrapped == Long.class
                || wrapped == Double.class
                || wrapped == Float.class
                || wrapped == Boolean.class
                || wrapped == Short.class
                || wrapped == Byte.class
                || wrapped == Character.class
                || wrapped == String.class
                || wrapped == Date.class;
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
        ArrayList<FlattenedFieldDescriptor> flattenedFields = new ArrayList<>();
        collectFieldGraph(root, "", List.of(), new LinkedHashSet<>(), 0, flattenedFields);
        LinkedHashMap<String, Class<?>> fieldTypes = new LinkedHashMap<>(Math.max(16, flattenedFields.size() * 2));
        ArrayList<String> fieldNames = new ArrayList<>(flattenedFields.size());
        for (int i = 0; i < flattenedFields.size(); i++) {
            FlattenedFieldDescriptor field = flattenedFields.get(i);
            fieldNames.add(field.fieldName());
            fieldTypes.put(field.fieldName(), field.fieldPath().leafType());
        }
        return new FieldGraphDescriptor(fieldNames, fieldTypes, flattenedFields);
    }

    private static void collectFieldGraph(Class<?> type,
                                          String prefix,
                                          List<Field> path,
                                          Set<Class<?>> activePath,
                                          int depth,
                                          List<FlattenedFieldDescriptor> flattenedFields) {
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

        try {
            List<Field> fields = getMutableFields(type);
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                String qualifiedName = qualify(prefix, field.getName());
                Class<?> fieldType = wrapPrimitive(field.getType());
                List<Field> fieldPath = appendPath(path, field);

                if (isSimpleType(fieldType)) {
                    flattenedFields.add(new FlattenedFieldDescriptor(
                            qualifiedName,
                            new ResolvedFieldPath(fieldPath, fieldType, true)
                    ));
                } else if (isTraversableType(fieldType)) {
                    collectFieldGraph(fieldType, qualifiedName, fieldPath, activePath, depth + 1, flattenedFields);
                }
            }
        } finally {
            activePath.remove(type);
        }
    }

    private static List<QueryField> extractQueryFields(Object bean,
                                                       List<FlattenedFieldDescriptor> flattenedFields) throws IllegalAccessException {
        List<QueryField> fields = new ArrayList<>(flattenedFields.size());
        for (int i = 0; i < flattenedFields.size(); i++) {
            FlattenedFieldDescriptor flattenedField = flattenedFields.get(i);
            QueryField field = new QueryField();
            field.setFieldName(flattenedField.fieldName());
            field.setValue(readResolvedFieldValue(bean, flattenedField.fieldPath()));
            fields.add(field);
        }
        return fields;
    }

    private static List<FlattenedFieldDescriptor> selectedFlattenedFields(FieldGraphDescriptor descriptor,
                                                                          Collection<String> selectedFieldNames) {
        List<FlattenedFieldDescriptor> flattenedFields = descriptor.flattenedFields();
        if (selectedFieldNames == null) {
            return flattenedFields;
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>(selectedFieldNames);
        if (selected.isEmpty()) {
            return flattenedFields;
        }
        ArrayList<FlattenedFieldDescriptor> filtered = new ArrayList<>(Math.min(selected.size(), flattenedFields.size()));
        for (int i = 0; i < flattenedFields.size(); i++) {
            FlattenedFieldDescriptor field = flattenedFields.get(i);
            if (selected.contains(field.fieldName())) {
                filtered.add(field);
            }
        }
        return filtered.isEmpty() ? flattenedFields : List.copyOf(filtered);
    }

    private static Object readResolvedFieldValue(Object bean, ResolvedFieldPath fieldPath) throws IllegalAccessException {
        Object current = bean;
        List<Field> fields = fieldPath.fields();
        for (int i = 0; i < fields.size(); i++) {
            if (current == null) {
                return null;
            }
            current = fields.get(i).get(current);
        }
        return current;
    }

    private static List<Field> appendPath(List<Field> path, Field field) {
        ArrayList<Field> fieldPath = new ArrayList<>(path.size() + 1);
        fieldPath.addAll(path);
        fieldPath.add(field);
        return fieldPath;
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
        List<String> parts = splitFieldPath(fieldName);
        if (parts.isEmpty()) {
            return MISSING_FIELD_PATH;
        }

        ArrayList<Field> fields = new ArrayList<>(parts.size());
        Class<?> currentType = rootType;

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
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

    private static ProjectionWritePlan projectionWritePlan(Class<?> projectionClass, List<QueryRow> rows) {
        return projectionWritePlanForSchema(projectionClass, projectionSourceSchema(rows));
    }

    private static ProjectionWritePlan projectionWritePlanForSchema(Class<?> projectionClass, List<String> sourceFieldSchema) {
        return PROJECTION_WRITE_PLAN_CACHE.computeIfAbsent(
                new ProjectionPlanCacheKey(projectionClass, sourceFieldSchema),
                key -> buildProjectionWritePlan(key.projectionClass(), key.sourceFieldSchema())
        );
    }

    private static ProjectionWritePlan buildProjectionWritePlan(Class<?> projectionClass, List<String> sourceFieldSchema) {
        ArrayList<ProjectionWriteStep> steps = new ArrayList<>(sourceFieldSchema.size());
        for (int i = 0; i < sourceFieldSchema.size(); i++) {
            String fieldName = sourceFieldSchema.get(i);
            ResolvedFieldPath fieldPath = resolveFieldPath(projectionClass, fieldName);
            if (!fieldPath.resolvable() || fieldPath.leafType() == null) {
                continue;
            }
            steps.add(new ProjectionWriteStep(i, fieldName, fieldPath));
        }
        return new ProjectionWritePlan(steps);
    }

    private static List<String> projectionSourceSchema(List<QueryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            QueryRow row = rows.get(rowIndex);
            if (row == null || row.getFields() == null || row.getFields().isEmpty()) {
                continue;
            }
            List<? extends QueryField> fields = row.getFields();
            ArrayList<String> schema = new ArrayList<>(fields.size());
            for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
                QueryField field = fields.get(fieldIndex);
                schema.add(field == null || field.getFieldName() == null ? "" : field.getFieldName());
            }
            return List.copyOf(schema);
        }
        return List.of();
    }

    private static void applyProjectionWritePlan(Object target,
                                                 List<? extends QueryField> fields,
                                                 ProjectionWritePlan plan) throws Exception {
        for (int stepIndex = 0; stepIndex < plan.steps().size(); stepIndex++) {
            ProjectionWriteStep step = plan.steps().get(stepIndex);
            QueryField sourceField = projectionSourceField(fields, step);
            if (sourceField == null || sourceField.getValue() == null) {
                continue;
            }
            Object value = ObjectUtil.castValue(sourceField.getValue(), step.fieldPath().leafType());
            setResolvedFieldValue(target, step.fieldPath(), value, step.fieldName());
        }
    }

    private static void applyProjectionWritePlan(Object target,
                                                 Object[] sourceValues,
                                                 int[] sourceIndexes,
                                                 ProjectionWritePlan plan) throws Exception {
        for (int stepIndex = 0; stepIndex < plan.steps().size(); stepIndex++) {
            ProjectionWriteStep step = plan.steps().get(stepIndex);
            int sourceIndex = step.sourceIndex();
            if (sourceIndexes != null) {
                if (sourceIndex < 0 || sourceIndex >= sourceIndexes.length) {
                    continue;
                }
                sourceIndex = sourceIndexes[sourceIndex];
            }
            if (sourceIndex < 0 || sourceIndex >= sourceValues.length) {
                continue;
            }
            Object rawValue = sourceValues[sourceIndex];
            if (rawValue == null) {
                continue;
            }
            Object value = ObjectUtil.castValue(rawValue, step.fieldPath().leafType());
            setResolvedFieldValue(target, step.fieldPath(), value, step.fieldName());
        }
    }

    private static QueryField projectionSourceField(List<? extends QueryField> fields, ProjectionWriteStep step) {
        if (step.sourceIndex() < fields.size()) {
            QueryField indexedField = fields.get(step.sourceIndex());
            if (indexedField != null && step.fieldName().equals(indexedField.getFieldName())) {
                return indexedField;
            }
        }
        for (int i = 0; i < fields.size(); i++) {
            QueryField field = fields.get(i);
            if (field != null && step.fieldName().equals(field.getFieldName())) {
                return field;
            }
        }
        return null;
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

        Field leaf = fields.get(fields.size() - 1);
        leaf.set(current, propertyValue);
    }

    private static Object instantiateNestedValue(Class<?> fieldType, String propertyName) throws Exception {
        if (fieldType == null || isSimpleType(fieldType) || fieldType.isEnum() || !isTraversableType(fieldType)) {
            throw new IllegalArgumentException("Cannot materialize nested path '" + propertyName + "'");
        }

        try {
            return noArgConstructor(fieldType).newInstance();
        } catch (ConstructorLookupException ex) {
            throw new IllegalArgumentException("Cannot materialize nested path '" + propertyName + "'", ex.getCause());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot materialize nested path '" + propertyName + "'", ex);
        }
    }

    private static <T> T instantiateNoArg(Class<T> type) throws ReflectiveOperationException {
        try {
            return type.cast(noArgConstructor(type).newInstance());
        } catch (ConstructorLookupException ex) {
            throw new IllegalArgumentException("Cannot instantiate type '" + type.getSimpleName() + "'", ex.getCause());
        }
    }

    private static Constructor<?> noArgConstructor(Class<?> type) {
        return NO_ARG_CTOR_CACHE.computeIfAbsent(type, key -> {
            try {
                Constructor<?> constructor = key.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor;
            } catch (Exception e) {
                throw new ConstructorLookupException(e);
            }
        });
    }

    private static String qualify(String prefix, String fieldName) {
        return (prefix == null || prefix.isEmpty()) ? fieldName : prefix + '.' + fieldName;
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

    private static void writeAlphaHex(char[] out, int offset, long value, int digits) {
        for (int i = digits - 1; i >= 0; i--) {
            out[offset + i] = ALPHA_HEX[(int) (value & 0xF)];
            value >>>= 4;
        }
    }

    private static List<String> splitFieldPath(String fieldName) {
        int length = fieldName.length();
        if (length == 0) {
            return List.of();
        }

        ArrayList<String> parts = new ArrayList<>(4);
        int start = 0;

        for (int i = 0; i < length; i++) {
            if (fieldName.charAt(i) == '.') {
                if (i == start) {
                    return List.of();
                }
                parts.add(fieldName.substring(start, i));
                start = i + 1;
            }
        }

        if (start == length) {
            return List.of();
        }

        parts.add(fieldName.substring(start));
        return parts;
    }

    private record FieldPathCacheKey(Class<?> rootType, String fieldName) {
    }

    private record ProjectionPlanCacheKey(Class<?> projectionClass, List<String> sourceFieldSchema) {
        private ProjectionPlanCacheKey(Class<?> projectionClass, List<String> sourceFieldSchema) {
            this.projectionClass = projectionClass;
            this.sourceFieldSchema = List.copyOf(sourceFieldSchema);
        }
    }

    private record ResolvedFieldPath(List<Field> fields, Class<?> leafType, boolean resolvable) {
        private ResolvedFieldPath(List<Field> fields, Class<?> leafType, boolean resolvable) {
            this.fields = List.copyOf(fields);
            this.leafType = leafType;
            this.resolvable = resolvable;
        }
    }

    private record FieldGraphDescriptor(List<String> fieldNames,
                                        Map<String, Class<?>> fieldTypes,
                                        List<FlattenedFieldDescriptor> flattenedFields) {
        private FieldGraphDescriptor(List<String> fieldNames,
                                     Map<String, Class<?>> fieldTypes,
                                     List<FlattenedFieldDescriptor> flattenedFields) {
            this.fieldNames = List.copyOf(fieldNames);
            this.fieldTypes = Collections.unmodifiableMap(new LinkedHashMap<>(fieldTypes));
            this.flattenedFields = List.copyOf(flattenedFields);
        }
    }

    private record FlattenedFieldDescriptor(String fieldName, ResolvedFieldPath fieldPath) {
    }

    private record ProjectionWritePlan(List<ProjectionWriteStep> steps) {
        private ProjectionWritePlan(List<ProjectionWriteStep> steps) {
            this.steps = List.copyOf(steps);
        }
    }

    private record ProjectionWriteStep(int sourceIndex, String fieldName, ResolvedFieldPath fieldPath) {
    }

    private static final class ConstructorLookupException extends RuntimeException {
        private ConstructorLookupException(Throwable cause) {
            super(cause);
        }
    }

    public static final class FlatRowReadPlan {
        private final List<FlattenedFieldDescriptor> flattenedFields;
        private final List<String> fieldNames;
        private final Map<String, Class<?>> fieldTypes;

        private FlatRowReadPlan(List<FlattenedFieldDescriptor> flattenedFields) {
            this.flattenedFields = List.copyOf(flattenedFields);
            ArrayList<String> orderedFieldNames = new ArrayList<>(flattenedFields.size());
            LinkedHashMap<String, Class<?>> orderedFieldTypes = new LinkedHashMap<>(Math.max(16, flattenedFields.size() * 2));
            for (int i = 0; i < flattenedFields.size(); i++) {
                FlattenedFieldDescriptor field = flattenedFields.get(i);
                orderedFieldNames.add(field.fieldName());
                orderedFieldTypes.put(field.fieldName(), field.fieldPath().leafType());
            }
            this.fieldNames = List.copyOf(orderedFieldNames);
            this.fieldTypes = Collections.unmodifiableMap(orderedFieldTypes);
        }

        public List<String> fieldNames() {
            return fieldNames;
        }

        public Map<String, Class<?>> fieldTypes() {
            return fieldTypes;
        }

        public int size() {
            return flattenedFields.size();
        }

        private List<FlattenedFieldDescriptor> flattenedFields() {
            return flattenedFields;
        }
    }
}
