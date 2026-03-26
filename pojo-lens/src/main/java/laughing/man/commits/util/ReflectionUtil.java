package laughing.man.commits.util;

import laughing.man.commits.annotations.Exclude;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.domain.RawQueryRow;
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
    private static final Map<FlatRowReadPlanCacheKey, FlatRowReadPlan> FLAT_ROW_READ_PLAN_CACHE = new ConcurrentHashMap<>();
    private static final Map<ProjectionPlanCacheKey, ProjectionWritePlan> PROJECTION_WRITE_PLAN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Constructor<?>> NO_ARG_CTOR_CACHE = new ConcurrentHashMap<>();

    private static final ResolvedFieldPath MISSING_FIELD_PATH = new ResolvedFieldPath(List.of(), null, false);

    private static final char[] ALPHA_HEX = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p'
    };

    private static final int DEFAULT_MAP_CAPACITY = 16;
    private static final int DEFAULT_PATH_PARTS_CAPACITY = 4;

    // UUID encoding layout constants
    private static final int UUID_TOTAL_CHARS = 36;
    private static final int UUID_SEG1_OFFSET = 0;
    private static final int UUID_SEP1_INDEX = 8;
    private static final int UUID_SEG2_OFFSET = 9;
    private static final int UUID_SEP2_INDEX = 13;
    private static final int UUID_SEG3_OFFSET = 14;
    private static final int UUID_SEP3_INDEX = 18;
    private static final int UUID_SEG4_OFFSET = 19;
    private static final int UUID_SEP4_INDEX = 23;
    private static final int UUID_SEG5_OFFSET = 24;
    private static final int UUID_SEG1_WIDTH = 8;
    private static final int UUID_SEG2_WIDTH = 4;
    private static final int UUID_SEG3_WIDTH = 4;
    private static final int UUID_SEG4_WIDTH = 4;
    private static final int UUID_SEG5_WIDTH = 12;
    private static final int UUID_MSB_SEG1_SHIFT = 32;
    private static final int UUID_MSB_SEG2_SHIFT = 16;
    private static final int UUID_LSB_SEG4_SHIFT = 48;
    private static final int HEX_NIBBLE_MASK_SHIFT = 4;
    private static final long HEX_NIBBLE_MASK = 0xFL;

    private ReflectionUtil() {
    }

    /**
     * Converts internal domain rows back to typed objects.
     */
    public static <T> List<T> toClassList(Class<T> cls, List<QueryRow> classes) {
        if (classes == null || classes.isEmpty()) {
            return new ArrayList<>(0);
        }
        if (QueryRow.class.isAssignableFrom(cls)) {
            List<T> rows = new ArrayList<>(classes.size());
            for (QueryRow row : classes) {
                if (row != null && cls.isInstance(row)) {
                    rows.add(cls.cast(row));
                }
            }
            return rows;
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
                if (row.getFieldCount() == 0 || plan.steps().isEmpty()) {
                    result.add(object);
                    continue;
                }

                applyProjectionWritePlan(object, row, plan);

                result.add(object);
            }
        } catch (Exception e) {
            LOG.error("Failed to Convert Objects [{}] to new List", cls.getSimpleName(), e);
            throw new IllegalStateException("Failed to convert domain rows to " + cls.getSimpleName(), e);
        }

        return result;
    }

    /**
     * Converts a single internal domain row to a typed object.
     */
    public static <T> T toClass(Class<T> cls, QueryRow row) {
        if (row == null) {
            return null;
        }
        if (QueryRow.class.isAssignableFrom(cls) && cls.isInstance(row)) {
            return cls.cast(row);
        }
        try {
            ProjectionWritePlan plan = projectionWritePlan(cls, List.of(row));
            T object = instantiateNoArg(cls);
            if (row.getFieldCount() == 0 || plan.steps().isEmpty()) {
                return object;
            }
            applyProjectionWritePlan(object, row, plan);
            return object;
        } catch (Exception e) {
            LOG.error("Failed to Convert Object [{}] to {}", row, cls.getSimpleName(), e);
            throw new IllegalStateException("Failed to convert domain row to " + cls.getSimpleName(), e);
        }
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
        if (QueryRow.class.isAssignableFrom(cls)) {
            List<T> queryRows = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                RawQueryRow queryRow = new RawQueryRow(
                        row != null ? row : new Object[0],
                        sourceFieldSchema != null ? sourceFieldSchema : List.of()
                );
                if (cls.isInstance(queryRow)) {
                    queryRows.add(cls.cast(queryRow));
                }
            }
            return queryRows;
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

        FlatRowReadPlan readPlan = compileFlatRowReadPlan(firstBean.getClass(), selectedFieldNames);
        int fieldCount = readPlan.size();
        List<String> sharedSchema = readPlan.fieldNames();
        ResolvedFieldPath[] paths = readPlan.fieldPaths();
        List<QueryRow> domainRows = new ArrayList<>(conversionList.size());

        for (int i = 0; i < conversionList.size(); i++) {
            Object currentBean = conversionList.get(i);
            if (currentBean == null) {
                continue;
            }

            try {
                Object[] values = new Object[fieldCount];
                for (int j = 0; j < fieldCount; j++) {
                    values[j] = readResolvedFieldValue(currentBean, paths[j]);
                }
                domainRows.add(new RawQueryRow(values, sharedSchema));
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
        char[] out = new char[UUID_TOTAL_CHARS];

        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        writeAlphaHex(out, UUID_SEG1_OFFSET, msb >>> UUID_MSB_SEG1_SHIFT, UUID_SEG1_WIDTH);
        out[UUID_SEP1_INDEX] = '_';
        writeAlphaHex(out, UUID_SEG2_OFFSET, msb >>> UUID_MSB_SEG2_SHIFT, UUID_SEG2_WIDTH);
        out[UUID_SEP2_INDEX] = '_';
        writeAlphaHex(out, UUID_SEG3_OFFSET, msb, UUID_SEG3_WIDTH);
        out[UUID_SEP3_INDEX] = '_';
        writeAlphaHex(out, UUID_SEG4_OFFSET, lsb >>> UUID_LSB_SEG4_SHIFT, UUID_SEG4_WIDTH);
        out[UUID_SEP4_INDEX] = '_';
        writeAlphaHex(out, UUID_SEG5_OFFSET, lsb, UUID_SEG5_WIDTH);

        return new String(out);
    }

    /**
     * Reads a mutable field value by name.
     */
    public static Object getFieldValue(Object cls, String fieldName) throws Exception {
        Object value = null;

        try {
            if (cls == null || StringUtil.isNullOrBlank(fieldName)) {
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
        List<String> normalizedSelection = normalizedSelectedFieldNames(selectedFieldNames);
        return FLAT_ROW_READ_PLAN_CACHE.computeIfAbsent(
                new FlatRowReadPlanCacheKey(root, normalizedSelection),
                key -> buildFlatRowReadPlan(key.rootType(), key.selectedFieldNames())
        );
    }

    public static Object[] readFlatRowValues(Object bean, FlatRowReadPlan plan) {
        if (bean == null || plan == null) {
            return new Object[0];
        }
        try {
            Object[] values = new Object[plan.size()];
            readFlatRowValues(bean, plan, values, 0);
            return values;
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
            LOG.error("Failed to read flattened row values [{}]", bean.getClass().getSimpleName(), e);
            throw new IllegalStateException("Failed to flatten object fields", e);
        }
    }

    public static void readFlatRowValues(Object bean,
                                         FlatRowReadPlan plan,
                                         Object[] target,
                                         int offset) throws IllegalAccessException {
        if (bean == null || plan == null || target == null || offset < 0) {
            return;
        }
        ResolvedFieldPath[] fieldPaths = plan.fieldPaths();
        for (int i = 0; i < fieldPaths.length && offset + i < target.length; i++) {
            target[offset + i] = readResolvedFieldValue(bean, fieldPaths[i]);
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
        Map<String, Field> byName = new HashMap<>(Math.max(DEFAULT_MAP_CAPACITY,fields.size() * 2));

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
        LinkedHashMap<String, Class<?>> fieldTypes = new LinkedHashMap<>(Math.max(DEFAULT_MAP_CAPACITY,flattenedFields.size() * 2));
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

    private static List<String> buildSchema(List<FlattenedFieldDescriptor> flattenedFields) {
        List<String> names = new ArrayList<>(flattenedFields.size());
        for (int i = 0; i < flattenedFields.size(); i++) {
            names.add(flattenedFields.get(i).fieldName());
        }
        return Collections.unmodifiableList(names);
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

    private static FlatRowReadPlan buildFlatRowReadPlan(Class<?> root, List<String> selectedFieldNames) {
        FieldGraphDescriptor descriptor = fieldGraph(root);
        return new FlatRowReadPlan(selectedFlattenedFields(descriptor, selectedFieldNames));
    }

    private static List<String> normalizedSelectedFieldNames(Collection<String> selectedFieldNames) {
        if (selectedFieldNames == null || selectedFieldNames.isEmpty()) {
            return List.of();
        }
        ArrayList<String> normalized = new ArrayList<>(selectedFieldNames.size());
        LinkedHashSet<String> seen = new LinkedHashSet<>(selectedFieldNames.size());
        for (String fieldName : selectedFieldNames) {
            if (seen.add(fieldName)) {
                normalized.add(fieldName);
            }
        }
        return Collections.unmodifiableList(normalized);
    }

    private static Object readResolvedFieldValue(Object bean, ResolvedFieldPath fieldPath) throws IllegalAccessException {
        if (bean == null || fieldPath == null || !fieldPath.resolvable()) {
            return null;
        }
        return fieldPath.read(bean);
    }

    private static List<Field> appendPath(List<Field> path, Field field) {
        ArrayList<Field> fieldPath = new ArrayList<>(path.size() + 1);
        fieldPath.addAll(path);
        fieldPath.add(field);
        return fieldPath;
    }

    private static ResolvedFieldPath resolveFieldPath(Class<?> rootType, String fieldName) {
        if (rootType == null || StringUtil.isNullOrBlank(fieldName)) {
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
        return projectionWritePlanForSchema(projectionClass, SchemaIndexUtil.firstQueryRowFieldNames(rows));
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

    private static void applyProjectionWritePlan(Object target,
                                                 List<? extends QueryField> fields,
                                                 ProjectionWritePlan plan) throws Exception {
        for (int stepIndex = 0; stepIndex < plan.steps().size(); stepIndex++) {
            ProjectionWriteStep step = plan.steps().get(stepIndex);
            QueryField sourceField = projectionSourceField(fields, step);
            if (sourceField == null || sourceField.getValue() == null) {
                continue;
            }
            Object rawValue = sourceField.getValue();
            Class<?> leafType = step.fieldPath().leafType();
            Object value = leafType.isInstance(rawValue)
                    ? rawValue
                    : ObjectUtil.castValue(rawValue, leafType);
            setResolvedFieldValue(target, step.fieldPath(), value, step.fieldName());
        }
    }

    private static void applyProjectionWritePlan(Object target,
                                                 QueryRow row,
                                                 ProjectionWritePlan plan) throws Exception {
        for (int stepIndex = 0; stepIndex < plan.steps().size(); stepIndex++) {
            ProjectionWriteStep step = plan.steps().get(stepIndex);
            Object rawValue = row.getValueAt(step.sourceIndex());
            if (rawValue == null) {
                continue;
            }
            Class<?> leafType = step.fieldPath().leafType();
            Object value = leafType.isInstance(rawValue)
                    ? rawValue
                    : ObjectUtil.castValue(rawValue, leafType);
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
            Class<?> leafType = step.fieldPath().leafType();
            Object value = leafType.isInstance(rawValue)
                    ? rawValue
                    : ObjectUtil.castValue(rawValue, leafType);
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
            out[offset + i] = ALPHA_HEX[(int) (value & HEX_NIBBLE_MASK)];
            value >>>= HEX_NIBBLE_MASK_SHIFT;
        }
    }

    private static List<String> splitFieldPath(String fieldName) {
        int length = fieldName.length();
        if (length == 0) {
            return List.of();
        }

        ArrayList<String> parts = new ArrayList<>(DEFAULT_PATH_PARTS_CAPACITY);
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

    private record FlatRowReadPlanCacheKey(Class<?> rootType, List<String> selectedFieldNames) {
        private FlatRowReadPlanCacheKey(Class<?> rootType, List<String> selectedFieldNames) {
            this.rootType = rootType;
            this.selectedFieldNames = Collections.unmodifiableList(new ArrayList<>(selectedFieldNames));
        }
    }

    private record ProjectionPlanCacheKey(Class<?> projectionClass, List<String> sourceFieldSchema) {
        private ProjectionPlanCacheKey(Class<?> projectionClass, List<String> sourceFieldSchema) {
            this.projectionClass = projectionClass;
            this.sourceFieldSchema = List.copyOf(sourceFieldSchema);
        }
    }

    private static final class ResolvedFieldPath {
        private static final int INLINE_THREE_FIELD_PATH = 3;

        private final List<Field> fields;
        private final Field[] readFields;
        private final Class<?> leafType;
        private final boolean resolvable;

        private ResolvedFieldPath(List<Field> fields, Class<?> leafType, boolean resolvable) {
            this.fields = List.copyOf(fields);
            this.readFields = this.fields.toArray(Field[]::new);
            this.leafType = leafType;
            this.resolvable = resolvable;
        }

        private List<Field> fields() {
            return fields;
        }

        private Class<?> leafType() {
            return leafType;
        }

        private boolean resolvable() {
            return resolvable;
        }

        private Object read(Object bean) throws IllegalAccessException {
            if (bean == null) {
                return null;
            }
            return switch (readFields.length) {
                case 0 -> null;
                case 1 -> readFields[0].get(bean);
                case 2 -> {
                    Object nested = readFields[0].get(bean);
                    yield nested == null ? null : readFields[1].get(nested);
                }
                case INLINE_THREE_FIELD_PATH -> {
                    Object nested = readFields[0].get(bean);
                    if (nested == null) {
                        yield null;
                    }
                    Object leafParent = readFields[1].get(nested);
                    yield leafParent == null ? null : readFields[2].get(leafParent);
                }
                default -> {
                    Object current = bean;
                    for (Field field : readFields) {
                        if (current == null) {
                            yield null;
                        }
                        current = field.get(current);
                    }
                    yield current;
                }
            };
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
        private final ResolvedFieldPath[] fieldPaths;
        private final List<String> fieldNames;
        private final Map<String, Class<?>> fieldTypes;

        private FlatRowReadPlan(List<FlattenedFieldDescriptor> flattenedFields) {
            this.flattenedFields = List.copyOf(flattenedFields);
            this.fieldPaths = new ResolvedFieldPath[flattenedFields.size()];
            ArrayList<String> orderedFieldNames = new ArrayList<>(flattenedFields.size());
            LinkedHashMap<String, Class<?>> orderedFieldTypes = new LinkedHashMap<>(Math.max(DEFAULT_MAP_CAPACITY,flattenedFields.size() * 2));
            for (int i = 0; i < flattenedFields.size(); i++) {
                FlattenedFieldDescriptor field = flattenedFields.get(i);
                fieldPaths[i] = field.fieldPath();
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

        private ResolvedFieldPath[] fieldPaths() {
            return fieldPaths;
        }
    }
}
