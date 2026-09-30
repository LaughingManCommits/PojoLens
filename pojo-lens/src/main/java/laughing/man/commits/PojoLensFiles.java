package laughing.man.commits;

import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.csv.internal.CsvLoaderSupport;
import laughing.man.commits.files.JsonLoadResult;
import laughing.man.commits.files.JsonOptions;
import laughing.man.commits.files.internal.JsonLoaderSupport;
import laughing.man.commits.files.internal.LoadSource;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;

/**
 * Boundary-loader surface for file-backed typed row onboarding.
 *
 * <p>Use this type when the format choice is part of the file boundary rather
 * than a separate product story. {@link PojoLensCsv} remains available as the
 * stable CSV-only convenience entry point over the same loader support.
 *
 * <p>Each format also accepts a {@link Reader} or {@link InputStream} for
 * classpath resources, uploads, and other non-file sources. Stream overloads
 * read the source to the end but never close it; the caller owns its
 * lifecycle. {@code InputStream} sources decode as strict UTF-8, matching
 * {@link Path} sources. Load reports carry a synthetic
 * {@code sourceName()} ({@code <reader>} or {@code <input-stream>}) and a
 * {@code null} {@code path()} for stream loads.
 */
public final class PojoLensFiles {

    private static final char TSV_DELIMITER = '\t';

    private PojoLensFiles() {
    }

    public static <T> List<T> csv(Path path, Class<T> rowType) {
        return PojoLensCsv.read(path, rowType);
    }

    public static <T> List<T> csv(Path path, Class<T> rowType, CsvOptions options) {
        return PojoLensCsv.read(path, rowType, options);
    }

    public static <T> CsvLoadResult<T> csvWithReport(Path path, Class<T> rowType) {
        return PojoLensCsv.readWithReport(path, rowType);
    }

    public static <T> CsvLoadResult<T> csvWithReport(Path path, Class<T> rowType, CsvOptions options) {
        return PojoLensCsv.readWithReport(path, rowType, options);
    }

    public static <T> List<T> tsv(Path path, Class<T> rowType) {
        return CsvLoaderSupport.read(path, rowType, defaultTsvOptions());
    }

    public static <T> List<T> tsv(Path path, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.read(path, rowType, tsvOptions(options));
    }

    public static <T> CsvLoadResult<T> tsvWithReport(Path path, Class<T> rowType) {
        return CsvLoaderSupport.readWithReport(path, rowType, defaultTsvOptions());
    }

    public static <T> CsvLoadResult<T> tsvWithReport(Path path, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.readWithReport(path, rowType, tsvOptions(options));
    }

    public static <T> List<T> json(Path path, Class<T> rowType) {
        return JsonLoaderSupport.readJson(path, rowType, JsonOptions.defaults());
    }

    public static <T> List<T> json(Path path, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJson(path, rowType, options);
    }

    public static <T> JsonLoadResult<T> jsonWithReport(Path path, Class<T> rowType) {
        return JsonLoaderSupport.readJsonWithReport(path, rowType, JsonOptions.defaults());
    }

    public static <T> JsonLoadResult<T> jsonWithReport(Path path, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonWithReport(path, rowType, options);
    }

    public static <T> List<T> jsonl(Path path, Class<T> rowType) {
        return JsonLoaderSupport.readJsonLines(path, rowType, JsonOptions.defaults());
    }

    public static <T> List<T> jsonl(Path path, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonLines(path, rowType, options);
    }

    public static <T> JsonLoadResult<T> jsonlWithReport(Path path, Class<T> rowType) {
        return JsonLoaderSupport.readJsonLinesWithReport(path, rowType, JsonOptions.defaults());
    }

    public static <T> JsonLoadResult<T> jsonlWithReport(Path path, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonLinesWithReport(path, rowType, options);
    }

    public static <T> List<T> csv(Reader reader, Class<T> rowType) {
        return CsvLoaderSupport.read(LoadSource.of(reader), rowType, CsvOptions.defaults());
    }

    public static <T> List<T> csv(Reader reader, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.read(LoadSource.of(reader), rowType, options);
    }

    public static <T> CsvLoadResult<T> csvWithReport(Reader reader, Class<T> rowType) {
        return CsvLoaderSupport.readWithReport(LoadSource.of(reader), rowType, CsvOptions.defaults());
    }

    public static <T> CsvLoadResult<T> csvWithReport(Reader reader, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.readWithReport(LoadSource.of(reader), rowType, options);
    }

    public static <T> List<T> tsv(Reader reader, Class<T> rowType) {
        return CsvLoaderSupport.read(LoadSource.of(reader), rowType, defaultTsvOptions());
    }

    public static <T> List<T> tsv(Reader reader, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.read(LoadSource.of(reader), rowType, tsvOptions(options));
    }

    public static <T> CsvLoadResult<T> tsvWithReport(Reader reader, Class<T> rowType) {
        return CsvLoaderSupport.readWithReport(LoadSource.of(reader), rowType, defaultTsvOptions());
    }

    public static <T> CsvLoadResult<T> tsvWithReport(Reader reader, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.readWithReport(LoadSource.of(reader), rowType, tsvOptions(options));
    }

    public static <T> List<T> json(Reader reader, Class<T> rowType) {
        return JsonLoaderSupport.readJson(LoadSource.of(reader), rowType, JsonOptions.defaults());
    }

    public static <T> List<T> json(Reader reader, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJson(LoadSource.of(reader), rowType, options);
    }

    public static <T> JsonLoadResult<T> jsonWithReport(Reader reader, Class<T> rowType) {
        return JsonLoaderSupport.readJsonWithReport(LoadSource.of(reader), rowType, JsonOptions.defaults());
    }

    public static <T> JsonLoadResult<T> jsonWithReport(Reader reader, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonWithReport(LoadSource.of(reader), rowType, options);
    }

    public static <T> List<T> jsonl(Reader reader, Class<T> rowType) {
        return JsonLoaderSupport.readJsonLines(LoadSource.of(reader), rowType, JsonOptions.defaults());
    }

    public static <T> List<T> jsonl(Reader reader, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonLines(LoadSource.of(reader), rowType, options);
    }

    public static <T> JsonLoadResult<T> jsonlWithReport(Reader reader, Class<T> rowType) {
        return JsonLoaderSupport.readJsonLinesWithReport(LoadSource.of(reader), rowType, JsonOptions.defaults());
    }

    public static <T> JsonLoadResult<T> jsonlWithReport(Reader reader, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonLinesWithReport(LoadSource.of(reader), rowType, options);
    }

    public static <T> List<T> csv(InputStream inputStream, Class<T> rowType) {
        return CsvLoaderSupport.read(LoadSource.of(inputStream), rowType, CsvOptions.defaults());
    }

    public static <T> List<T> csv(InputStream inputStream, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.read(LoadSource.of(inputStream), rowType, options);
    }

    public static <T> CsvLoadResult<T> csvWithReport(InputStream inputStream, Class<T> rowType) {
        return CsvLoaderSupport.readWithReport(LoadSource.of(inputStream), rowType, CsvOptions.defaults());
    }

    public static <T> CsvLoadResult<T> csvWithReport(InputStream inputStream, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.readWithReport(LoadSource.of(inputStream), rowType, options);
    }

    public static <T> List<T> tsv(InputStream inputStream, Class<T> rowType) {
        return CsvLoaderSupport.read(LoadSource.of(inputStream), rowType, defaultTsvOptions());
    }

    public static <T> List<T> tsv(InputStream inputStream, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.read(LoadSource.of(inputStream), rowType, tsvOptions(options));
    }

    public static <T> CsvLoadResult<T> tsvWithReport(InputStream inputStream, Class<T> rowType) {
        return CsvLoaderSupport.readWithReport(LoadSource.of(inputStream), rowType, defaultTsvOptions());
    }

    public static <T> CsvLoadResult<T> tsvWithReport(InputStream inputStream, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.readWithReport(LoadSource.of(inputStream), rowType, tsvOptions(options));
    }

    public static <T> List<T> json(InputStream inputStream, Class<T> rowType) {
        return JsonLoaderSupport.readJson(LoadSource.of(inputStream), rowType, JsonOptions.defaults());
    }

    public static <T> List<T> json(InputStream inputStream, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJson(LoadSource.of(inputStream), rowType, options);
    }

    public static <T> JsonLoadResult<T> jsonWithReport(InputStream inputStream, Class<T> rowType) {
        return JsonLoaderSupport.readJsonWithReport(LoadSource.of(inputStream), rowType, JsonOptions.defaults());
    }

    public static <T> JsonLoadResult<T> jsonWithReport(InputStream inputStream, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonWithReport(LoadSource.of(inputStream), rowType, options);
    }

    public static <T> List<T> jsonl(InputStream inputStream, Class<T> rowType) {
        return JsonLoaderSupport.readJsonLines(LoadSource.of(inputStream), rowType, JsonOptions.defaults());
    }

    public static <T> List<T> jsonl(InputStream inputStream, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonLines(LoadSource.of(inputStream), rowType, options);
    }

    public static <T> JsonLoadResult<T> jsonlWithReport(InputStream inputStream, Class<T> rowType) {
        return JsonLoaderSupport.readJsonLinesWithReport(LoadSource.of(inputStream), rowType, JsonOptions.defaults());
    }

    public static <T> JsonLoadResult<T> jsonlWithReport(InputStream inputStream, Class<T> rowType, JsonOptions options) {
        return JsonLoaderSupport.readJsonLinesWithReport(LoadSource.of(inputStream), rowType, options);
    }

    private static CsvOptions defaultTsvOptions() {
        return CsvOptions.defaults().toBuilder()
                .delimiter(TSV_DELIMITER)
                .build();
    }

    private static CsvOptions tsvOptions(CsvOptions options) {
        return options == null
                ? null
                : options.toBuilder()
                .delimiter(TSV_DELIMITER)
                .build();
    }
}
