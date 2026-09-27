package laughing.man.commits.files;

import laughing.man.commits.PojoLensFiles;
import laughing.man.commits.PojoLensRuntime;
import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.files.JsonLoadResult;
import laughing.man.commits.files.JsonOptions;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-scoped file-boundary loader surface with instance-owned defaults.
 *
 * <p>{@link Reader} and {@link InputStream} overloads follow the same
 * stream-ownership and UTF-8 rules as {@link PojoLensFiles}.
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

    public <T> List<T> csv(Reader reader, Class<T> rowType) {
        return PojoLensFiles.csv(reader, rowType, runtime.getCsvDefaults());
    }

    public <T> List<T> csv(Reader reader, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.csv(reader, rowType, options);
    }

    public <T> CsvLoadResult<T> csvWithReport(Reader reader, Class<T> rowType) {
        return PojoLensFiles.csvWithReport(reader, rowType, runtime.getCsvDefaults());
    }

    public <T> CsvLoadResult<T> csvWithReport(Reader reader, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.csvWithReport(reader, rowType, options);
    }

    public <T> List<T> tsv(Reader reader, Class<T> rowType) {
        return PojoLensFiles.tsv(reader, rowType, runtime.getCsvDefaults());
    }

    public <T> List<T> tsv(Reader reader, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.tsv(reader, rowType, options);
    }

    public <T> CsvLoadResult<T> tsvWithReport(Reader reader, Class<T> rowType) {
        return PojoLensFiles.tsvWithReport(reader, rowType, runtime.getCsvDefaults());
    }

    public <T> CsvLoadResult<T> tsvWithReport(Reader reader, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.tsvWithReport(reader, rowType, options);
    }

    public <T> List<T> json(Reader reader, Class<T> rowType) {
        return PojoLensFiles.json(reader, rowType, runtime.getJsonDefaults());
    }

    public <T> List<T> json(Reader reader, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.json(reader, rowType, options);
    }

    public <T> JsonLoadResult<T> jsonWithReport(Reader reader, Class<T> rowType) {
        return PojoLensFiles.jsonWithReport(reader, rowType, runtime.getJsonDefaults());
    }

    public <T> JsonLoadResult<T> jsonWithReport(Reader reader, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonWithReport(reader, rowType, options);
    }

    public <T> List<T> jsonl(Reader reader, Class<T> rowType) {
        return PojoLensFiles.jsonl(reader, rowType, runtime.getJsonDefaults());
    }

    public <T> List<T> jsonl(Reader reader, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonl(reader, rowType, options);
    }

    public <T> JsonLoadResult<T> jsonlWithReport(Reader reader, Class<T> rowType) {
        return PojoLensFiles.jsonlWithReport(reader, rowType, runtime.getJsonDefaults());
    }

    public <T> JsonLoadResult<T> jsonlWithReport(Reader reader, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonlWithReport(reader, rowType, options);
    }

    public <T> List<T> csv(InputStream inputStream, Class<T> rowType) {
        return PojoLensFiles.csv(inputStream, rowType, runtime.getCsvDefaults());
    }

    public <T> List<T> csv(InputStream inputStream, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.csv(inputStream, rowType, options);
    }

    public <T> CsvLoadResult<T> csvWithReport(InputStream inputStream, Class<T> rowType) {
        return PojoLensFiles.csvWithReport(inputStream, rowType, runtime.getCsvDefaults());
    }

    public <T> CsvLoadResult<T> csvWithReport(InputStream inputStream, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.csvWithReport(inputStream, rowType, options);
    }

    public <T> List<T> tsv(InputStream inputStream, Class<T> rowType) {
        return PojoLensFiles.tsv(inputStream, rowType, runtime.getCsvDefaults());
    }

    public <T> List<T> tsv(InputStream inputStream, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.tsv(inputStream, rowType, options);
    }

    public <T> CsvLoadResult<T> tsvWithReport(InputStream inputStream, Class<T> rowType) {
        return PojoLensFiles.tsvWithReport(inputStream, rowType, runtime.getCsvDefaults());
    }

    public <T> CsvLoadResult<T> tsvWithReport(InputStream inputStream, Class<T> rowType, CsvOptions options) {
        return PojoLensFiles.tsvWithReport(inputStream, rowType, options);
    }

    public <T> List<T> json(InputStream inputStream, Class<T> rowType) {
        return PojoLensFiles.json(inputStream, rowType, runtime.getJsonDefaults());
    }

    public <T> List<T> json(InputStream inputStream, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.json(inputStream, rowType, options);
    }

    public <T> JsonLoadResult<T> jsonWithReport(InputStream inputStream, Class<T> rowType) {
        return PojoLensFiles.jsonWithReport(inputStream, rowType, runtime.getJsonDefaults());
    }

    public <T> JsonLoadResult<T> jsonWithReport(InputStream inputStream, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonWithReport(inputStream, rowType, options);
    }

    public <T> List<T> jsonl(InputStream inputStream, Class<T> rowType) {
        return PojoLensFiles.jsonl(inputStream, rowType, runtime.getJsonDefaults());
    }

    public <T> List<T> jsonl(InputStream inputStream, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonl(inputStream, rowType, options);
    }

    public <T> JsonLoadResult<T> jsonlWithReport(InputStream inputStream, Class<T> rowType) {
        return PojoLensFiles.jsonlWithReport(inputStream, rowType, runtime.getJsonDefaults());
    }

    public <T> JsonLoadResult<T> jsonlWithReport(InputStream inputStream, Class<T> rowType, JsonOptions options) {
        return PojoLensFiles.jsonlWithReport(inputStream, rowType, options);
    }
}
