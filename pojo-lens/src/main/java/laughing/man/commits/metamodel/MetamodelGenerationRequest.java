package laughing.man.commits.metamodel;

import java.util.Objects;

/**
 * Declarative metamodel generation request for build-time source writing.
 */
public final class MetamodelGenerationRequest {

    private final Class<?> modelClass;
    private final String packageName;
    private final String simpleName;
    private final MetamodelGenerationMode mode;

    private MetamodelGenerationRequest(Class<?> modelClass,
                                       String packageName,
                                       String simpleName,
                                       MetamodelGenerationMode mode) {
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.packageName = packageName;
        this.simpleName = simpleName;
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    public static MetamodelGenerationRequest strings(Class<?> modelClass) {
        Objects.requireNonNull(modelClass, "modelClass must not be null");
        return new MetamodelGenerationRequest(
                modelClass,
                modelClass.getPackageName(),
                modelClass.getSimpleName() + "Fields",
                MetamodelGenerationMode.STRINGS
        );
    }

    public static MetamodelGenerationRequest strings(Class<?> modelClass,
                                                     String packageName,
                                                     String simpleName) {
        return new MetamodelGenerationRequest(modelClass, packageName, simpleName, MetamodelGenerationMode.STRINGS);
    }

    public static MetamodelGenerationRequest typed(Class<?> modelClass) {
        Objects.requireNonNull(modelClass, "modelClass must not be null");
        return new MetamodelGenerationRequest(
                modelClass,
                modelClass.getPackageName(),
                modelClass.getSimpleName() + "TypedFields",
                MetamodelGenerationMode.TYPED
        );
    }

    public static MetamodelGenerationRequest typed(Class<?> modelClass,
                                                   String packageName,
                                                   String simpleName) {
        return new MetamodelGenerationRequest(modelClass, packageName, simpleName, MetamodelGenerationMode.TYPED);
    }

    public Class<?> modelClass() {
        return modelClass;
    }

    public String packageName() {
        return packageName;
    }

    public String simpleName() {
        return simpleName;
    }

    public MetamodelGenerationMode mode() {
        return mode;
    }
}
