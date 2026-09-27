package laughing.man.commits.metamodel;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result of a single metamodel generation/write operation.
 */
public final class MetamodelGenerationResult {

    private final MetamodelGenerationRequest request;
    private final FieldMetamodel metamodel;
    private final Path outputPath;

    public MetamodelGenerationResult(MetamodelGenerationRequest request,
                                     FieldMetamodel metamodel,
                                     Path outputPath) {
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.metamodel = Objects.requireNonNull(metamodel, "metamodel must not be null");
        this.outputPath = Objects.requireNonNull(outputPath, "outputPath must not be null");
    }

    public MetamodelGenerationRequest request() {
        return request;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "FieldMetamodel is immutable: final fields with defensive copies."
    )
    public FieldMetamodel metamodel() {
        return metamodel;
    }

    public Path outputPath() {
        return outputPath;
    }
}
