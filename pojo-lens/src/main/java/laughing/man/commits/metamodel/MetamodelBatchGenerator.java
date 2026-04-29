package laughing.man.commits.metamodel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Batch metamodel writer for build-time source generation.
 */
public final class MetamodelBatchGenerator {

    private MetamodelBatchGenerator() {
    }

    public static List<MetamodelGenerationResult> writeDefaultStrings(Path outputDirectory, Class<?>... modelClasses) {
        return write(outputDirectory, defaultRequests(MetamodelGenerationMode.STRINGS, modelClasses));
    }

    public static List<MetamodelGenerationResult> writeDefaultTyped(Path outputDirectory, Class<?>... modelClasses) {
        return write(outputDirectory, defaultRequests(MetamodelGenerationMode.TYPED, modelClasses));
    }

    public static List<MetamodelGenerationResult> write(Path outputDirectory,
                                                        List<MetamodelGenerationRequest> requests) {
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        Objects.requireNonNull(requests, "requests must not be null");

        ArrayList<MetamodelGenerationResult> results = new ArrayList<>(requests.size());
        LinkedHashSet<String> generatedTargets = new LinkedHashSet<>();
        for (MetamodelGenerationRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("requests must not contain null entries");
            }
            FieldMetamodel metamodel = generate(request);
            String target = metamodel.qualifiedName();
            if (!generatedTargets.add(target)) {
                throw new IllegalArgumentException("Duplicate metamodel target '" + target + "'");
            }
            Path outputPath = metamodel.writeTo(outputDirectory);
            results.add(new MetamodelGenerationResult(request, metamodel, outputPath));
        }
        return List.copyOf(results);
    }

    private static List<MetamodelGenerationRequest> defaultRequests(MetamodelGenerationMode mode,
                                                                    Class<?>... modelClasses) {
        Objects.requireNonNull(modelClasses, "modelClasses must not be null");
        ArrayList<MetamodelGenerationRequest> requests = new ArrayList<>(modelClasses.length);
        for (Class<?> modelClass : modelClasses) {
            requests.add(mode == MetamodelGenerationMode.TYPED
                    ? MetamodelGenerationRequest.typed(modelClass)
                    : MetamodelGenerationRequest.strings(modelClass));
        }
        return List.copyOf(requests);
    }

    private static FieldMetamodel generate(MetamodelGenerationRequest request) {
        return request.mode() == MetamodelGenerationMode.TYPED
                ? FieldMetamodelGenerator.generateTyped(
                        request.modelClass(),
                        request.packageName(),
                        request.simpleName())
                : FieldMetamodelGenerator.generate(
                        request.modelClass(),
                        request.packageName(),
                        request.simpleName());
    }
}
