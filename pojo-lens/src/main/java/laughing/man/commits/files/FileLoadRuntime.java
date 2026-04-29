package laughing.man.commits.files;

import laughing.man.commits.PojoLensFiles;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.files.JsonLoadResult;
import laughing.man.commits.files.JsonOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-scoped file-boundary loader surface with instance-owned defaults.
 */
public final class FileLoadRuntime {

    private final PojoLensRuntime runtime;

    public FileLoadRuntime(PojoLensRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    public <T> List<T> csv(Path path, Class<T> rowType) {
        return PojoLensFiles.csv(path, rowType, runtime.getCsvDefaults());
    }

    public <T> List<T> csv(Path path, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.csv(path, rowType, options);
    }

    public <T> CsvLoadResult<T> csvWithReport(Path path, Class<T> rowType) {
        return PojoLensFiles.csvWithReport(path, rowType, runtime.getCsvDefaults());
    }

    public <T> CsvLoadResult<T> csvWithReport(Path path, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.csvWithReport(path, rowType, options);
    }

    public <T> List<T> tsv(Path path, Class<T> rowType) {
        return PojoLensFiles.tsv(path, rowType, runtime.getCsvDefaults());
    }

    public <T> List<T> tsv(Path path, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.tsv(path, rowType, options);
    }

    public <T> CsvLoadResult<T> tsvWithReport(Path path, Class<T> rowType) {
        return PojoLensFiles.tsvWithReport(path, rowType, runtime.getCsvDefaults());
    }

    public <T> CsvLoadResult<T> tsvWithReport(Path path, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.tsvWithReport(path, rowType, options);
    }

    public <T> List<T> json(Path path, Class<T> rowType) {
        return PojoLensFiles.json(path, rowType, runtime.getJsonDefaults());
    }

    public <T> List<T> json(Path path, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.json(path, rowType, options);
    }

    public <T> JsonLoadResult<T> jsonWithReport(Path path, Class<T> rowType) {
        return PojoLensFiles.jsonWithReport(path, rowType, runtime.getJsonDefaults());
    }

    public <T> JsonLoadResult<T> jsonWithReport(Path path, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonWithReport(path, rowType, options);
    }

    public <T> List<T> jsonl(Path path, Class<T> rowType) {
        return PojoLensFiles.jsonl(path, rowType, runtime.getJsonDefaults());
    }

    public <T> List<T> jsonl(Path path, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonl(path, rowType, options);
    }

    public <T> JsonLoadResult<T> jsonlWithReport(Path path, Class<T> rowType) {
        return PojoLensFiles.jsonlWithReport(path, rowType, runtime.getJsonDefaults());
    }

    public <T> JsonLoadResult<T> jsonlWithReport(Path path, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonlWithReport(path, rowType, options);
    }
}
