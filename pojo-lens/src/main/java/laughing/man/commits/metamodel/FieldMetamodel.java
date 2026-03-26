package laughing.man.commits.metamodel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic generated-field metamodel descriptor and source payload.
 */
public final class FieldMetamodel {

    private final Class<?> modelClass;
    private final String packageName;
    private final String simpleName;
    private final List<String> fieldNames;
    private final Map<String, String> constants;
    private final String source;

    FieldMetamodel(Class<?> modelClass,
                   String packageName,
                   String simpleName,
                   List<String> fieldNames,
                   Map<String, String> constants,
                   String source) {
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.packageName = packageName == null ? "" : packageName;
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName must not be null");
        this.fieldNames = List.copyOf(fieldNames);
        this.constants = Map.copyOf(constants);
        this.source = Objects.requireNonNull(source, "source must not be null");
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

    public List<String> fieldNames() {
        return fieldNames;
    }

    public Map<String, String> constants() {
        return constants;
    }

    public String source() {
        return source;
    }

    public String qualifiedName() {
        if (packageName.isEmpty()) {
            return simpleName;
        }
        return packageName + "." + simpleName;
    }

    public Path writeTo(Path outputDirectory) {
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        Path packageDirectory = packageName.isEmpty()
                ? outputDirectory
                : outputDirectory.resolve(packageName.replace('.', '/'));
        try {
            Files.createDirectories(packageDirectory);
            Path javaFile = packageDirectory.resolve(simpleName + ".java");
            Files.writeString(javaFile, source, StandardCharsets.UTF_8);
            return javaFile;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write metamodel source for " + qualifiedName(), e);
        }
    }
}

