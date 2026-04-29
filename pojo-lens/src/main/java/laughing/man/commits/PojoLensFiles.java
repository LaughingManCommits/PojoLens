package laughing.man.commits;

import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.csv.internal.CsvLoaderSupport;
import laughing.man.commits.files.JsonLoadResult;
import laughing.man.commits.files.JsonOptions;
import laughing.man.commits.files.internal.JsonLoaderSupport;

import java.nio.file.Path;
import java.util.List;

/**
 * Boundary-loader surface for file-backed typed row onboarding.
 *
 * <p>Use this type when the format choice is part of the file boundary rather
 * than a separate product story. {@link PojoLensCsv} remains available as the
 * stable CSV-only convenience entry point over the same loader support.
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
