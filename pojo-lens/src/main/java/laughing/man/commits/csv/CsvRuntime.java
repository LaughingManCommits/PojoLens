package laughing.man.commits.csv;

import laughing.man.commits.PojoLensCsv;
import laughing.man.commits.PojoLensRuntime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-scoped entry point for CSV loading with instance-owned defaults.
 */
public final class CsvRuntime {

    private final PojoLensRuntime runtime;

    public CsvRuntime(PojoLensRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    public <T> List<T> read(Path path, Class<T> rowType) {
        return PojoLensCsv.read(path, rowType, runtime.getCsvDefaults());
    }

    public <T> List<T> read(Path path, Class<T> rowType, CsvOptions options) {
        return PojoLensCsv.read(path, rowType, options);
    }
}
