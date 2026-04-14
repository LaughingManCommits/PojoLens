package laughing.man.commits;

import laughing.man.commits.csv.CsvOptions;
import laughing.man.commits.csv.CsvLoadResult;
import laughing.man.commits.csv.internal.CsvLoaderSupport;

import java.nio.file.Path;
import java.util.List;

/**
 * Boundary adapter for loading typed rows from CSV into the existing engine.
 */
public final class PojoLensCsv {

    private PojoLensCsv() {
    }

    public static <T> List<T> read(Path path, Class<T> rowType) {
        return read(path, rowType, CsvOptions.defaults());
    }

    public static <T> List<T> read(Path path, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.read(path, rowType, options);
    }

    public static <T> CsvLoadResult<T> readWithReport(Path path, Class<T> rowType) {
        return readWithReport(path, rowType, CsvOptions.defaults());
    }

    public static <T> CsvLoadResult<T> readWithReport(Path path, Class<T> rowType, CsvOptions options) {
        return CsvLoaderSupport.readWithReport(path, rowType, options);
    }
}
