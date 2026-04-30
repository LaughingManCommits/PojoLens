package laughing.man.commits.metamodel;

import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Annotation processor that emits typed field constants during javac source generation.
 */
@SupportedAnnotationTypes("laughing.man.commits.annotations.GeneratePojoLensTypedFields")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public final class PojoLensTypedFieldsProcessor extends AbstractProcessor {

    private static final int MAX_FIELD_GRAPH_DEPTH = 8;

    private final Set<String> generatedQualifiedNames = new LinkedHashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(GeneratePojoLensTypedFields.class)) {
            if (!(element instanceof TypeElement modelType)) {
                error(element, "PLM-AP-001 @GeneratePojoLensTypedFields can only be used on classes.");
                continue;
            }
            NestingKind nestingKind = modelType.getNestingKind();
            if (nestingKind != NestingKind.TOP_LEVEL && nestingKind != NestingKind.MEMBER) {
                error(element, "PLM-AP-002 @GeneratePojoLensTypedFields requires a named top-level or nested class.");
                continue;
            }
            generate(modelType);
        }
        return true;
    }

    private void generate(TypeElement modelType) {
        GeneratePojoLensTypedFields annotation = modelType.getAnnotation(GeneratePojoLensTypedFields.class);
        String modelPackage = packageName(modelType);
        String generatedPackage;
        String simpleName;
        try {
            generatedPackage = annotation.packageName().isBlank()
                    ? modelPackage
                    : FieldMetamodelGenerator.normalizePackageName(annotation.packageName());
            simpleName = annotation.simpleName().isBlank()
                    ? modelType.getSimpleName() + "TypedFields"
                    : FieldMetamodelGenerator.normalizeSimpleName(annotation.simpleName());
        } catch (IllegalArgumentException ex) {
            error(modelType, "PLM-AP-006 Invalid generated metamodel target: " + ex.getMessage());
            return;
        }
        String qualifiedName = generatedPackage.isEmpty() ? simpleName : generatedPackage + "." + simpleName;

        if (!generatedQualifiedNames.add(qualifiedName)) {
            error(modelType, "PLM-AP-003 Duplicate generated metamodel target '" + qualifiedName + "'.");
            return;
        }

        ArrayList<FieldDescriptor> fields = new ArrayList<>();
        collectFieldGraph(modelType, "", 0, new LinkedHashSet<>(), fields);
        fields.sort(Comparator.comparing(FieldDescriptor::fieldName));

        List<String> fieldNames = fields.stream().map(FieldDescriptor::fieldName).toList();
        Map<String, String> constants = FieldMetamodelGenerator.buildConstantMap(fieldNames);
        Map<String, FieldMetamodelGenerator.FieldTypeNames> fieldTypes = new LinkedHashMap<>();
        for (FieldDescriptor field : fields) {
            fieldTypes.put(field.fieldName(), field.typeNames());
        }

        String source = FieldMetamodelGenerator.renderTypedSource(
                generatedPackage,
                simpleName,
                modelType.getSimpleName().toString(),
                modelType.getQualifiedName().toString(),
                modelPackage,
                generatedPackage,
                constants,
                fieldTypes
        );
        writeSource(modelType, qualifiedName, source);
    }

    private void collectFieldGraph(TypeElement type,
                                   String prefix,
                                   int depth,
                                   Set<String> activePath,
                                   List<FieldDescriptor> fields) {
        if (depth > MAX_FIELD_GRAPH_DEPTH) {
            error(type, "PLM-AP-004 Field graph depth exceeds max depth of " + MAX_FIELD_GRAPH_DEPTH + ".");
            return;
        }
        String qualifiedName = type.getQualifiedName().toString();
        if (!activePath.add(qualifiedName)) {
            return;
        }
        try {
            for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                if (!isQueryableField(field)) {
                    continue;
                }
                String fieldName = qualify(prefix, field.getSimpleName().toString());
                TypeMirror fieldType = field.asType();
                if (isSimpleLeaf(fieldType)) {
                    fields.add(new FieldDescriptor(fieldName, fieldTypeNames(fieldType)));
                    continue;
                }
                TypeElement nestedType = traversableElement(fieldType);
                if (nestedType != null) {
                    collectFieldGraph(nestedType, fieldName, depth + 1, activePath, fields);
                }
            }
        } finally {
            activePath.remove(qualifiedName);
        }
    }

    private boolean isQueryableField(VariableElement field) {
        Set<Modifier> modifiers = field.getModifiers();
        if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.FINAL)) {
            return false;
        }
        for (javax.lang.model.element.AnnotationMirror mirror : field.getAnnotationMirrors()) {
            if ("laughing.man.commits.annotations.Exclude"
                    .equals(mirror.getAnnotationType().asElement().toString())) {
                return false;
            }
        }
        return true;
    }

    private boolean isSimpleLeaf(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return true;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        Element element = ((DeclaredType) type).asElement();
        if (!(element instanceof TypeElement typeElement)) {
            return false;
        }
        return typeElement.getKind() == ElementKind.ENUM
                || isKnownSimpleType(typeElement.getQualifiedName().toString());
    }

    private TypeElement traversableElement(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED || isSimpleLeaf(type)) {
            return null;
        }
        Element element = ((DeclaredType) type).asElement();
        if (!(element instanceof TypeElement typeElement) || !isUserDefined(typeElement)) {
            return null;
        }
        return typeElement;
    }

    private FieldMetamodelGenerator.FieldTypeNames fieldTypeNames(TypeMirror rawType) {
        if (rawType.getKind().isPrimitive()) {
            TypeElement boxed = processingEnv.getTypeUtils().boxedClass((PrimitiveType) rawType);
            return declaredTypeNames(boxed, boxed.asType());
        }
        if (rawType.getKind() == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) rawType;
            FieldMetamodelGenerator.FieldTypeNames component = fieldTypeNames(arrayType.getComponentType());
            String sourceName = component.sourceName() + "[]";
            return new FieldMetamodelGenerator.FieldTypeNames(
                    sourceName,
                    component.importName(),
                    sourceName + ".class"
            );
        }
        if (rawType.getKind() == TypeKind.DECLARED) {
            Element element = ((DeclaredType) rawType).asElement();
            if (element instanceof TypeElement typeElement) {
                return declaredTypeNames(typeElement, rawType);
            }
        }
        return new FieldMetamodelGenerator.FieldTypeNames("Object", null, "Object.class");
    }

    private FieldMetamodelGenerator.FieldTypeNames declaredTypeNames(TypeElement typeElement, TypeMirror rawType) {
        TypeMirror erased = processingEnv.getTypeUtils().erasure(rawType);
        TypeElement erasedElement = (TypeElement) processingEnv.getTypeUtils().asElement(erased);
        String sourceName = erasedElement.getSimpleName().toString();
        String importName = importName(typeElement);
        return new FieldMetamodelGenerator.FieldTypeNames(sourceName, importName, sourceName + ".class");
    }

    private String importName(TypeElement typeElement) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        String packageName = packageElement == null ? "" : packageElement.getQualifiedName().toString();
        if (packageName.isEmpty() || "java.lang".equals(packageName)) {
            return null;
        }
        return typeElement.getQualifiedName().toString();
    }

    private boolean isUserDefined(TypeElement typeElement) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(typeElement);
        if (packageElement == null) {
            return true;
        }
        String packageName = packageElement.getQualifiedName().toString();
        return !packageName.startsWith("java.")
                && !packageName.startsWith("javax.")
                && !packageName.startsWith("jdk.");
    }

    private boolean isKnownSimpleType(String qualifiedName) {
        return "java.lang.Integer".equals(qualifiedName)
                || "java.lang.Long".equals(qualifiedName)
                || "java.lang.Double".equals(qualifiedName)
                || "java.lang.Float".equals(qualifiedName)
                || "java.lang.Boolean".equals(qualifiedName)
                || "java.lang.Short".equals(qualifiedName)
                || "java.lang.Byte".equals(qualifiedName)
                || "java.lang.Character".equals(qualifiedName)
                || "java.lang.String".equals(qualifiedName)
                || "java.util.Date".equals(qualifiedName)
                || "java.time.Instant".equals(qualifiedName)
                || "java.time.LocalDate".equals(qualifiedName)
                || "java.time.LocalDateTime".equals(qualifiedName)
                || "java.time.OffsetDateTime".equals(qualifiedName)
                || "java.time.ZonedDateTime".equals(qualifiedName);
    }

    private void writeSource(TypeElement modelType, String qualifiedName, String source) {
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject fileObject = filer.createSourceFile(qualifiedName, modelType);
            try (Writer writer = fileObject.openWriter()) {
                writer.write(source);
            }
        } catch (IOException e) {
            error(modelType, "PLM-AP-005 Failed to write generated metamodel '" + qualifiedName + "': "
                    + e.getMessage());
        }
    }

    private String packageName(TypeElement type) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(type);
        return packageElement == null ? "" : packageElement.getQualifiedName().toString();
    }

    private String qualify(String prefix, String fieldName) {
        return prefix == null || prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private record FieldDescriptor(String fieldName,
                                   FieldMetamodelGenerator.FieldTypeNames typeNames) {
    }
}
