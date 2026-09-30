package laughing.man.commits.files.internal;

import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Internal text source for file-boundary loaders: a file path, or a caller-owned
 * {@link Reader} or {@link InputStream}.
 *
 * <p>Path sources are opened and closed by the loader. Stream sources are read
 * to the end but never closed; the caller that opened them owns their lifecycle.
 * Byte sources decode as strict UTF-8, matching path sources.
 */
public final class LoadSource {

    public static final String READER_SOURCE_NAME = "<reader>";
    public static final String INPUT_STREAM_SOURCE_NAME = "<input-stream>";

    private final Path path;
    private final Reader reader;
    private final String name;
    private final String argumentName;

    private LoadSource(Path path, Reader reader, String name, String argumentName) {
        this.path = path;
        this.reader = reader;
        this.name = name;
        this.argumentName = argumentName;
    }

    public static LoadSource of(Path path) {
        return new LoadSource(path, null, path == null ? null : path.toString(), "path");
    }

    public static LoadSource of(Reader reader) {
        return new LoadSource(null, reader, READER_SOURCE_NAME, "reader");
    }

    public static LoadSource of(InputStream inputStream) {
        Reader reader = inputStream == null
                ? null
                : new InputStreamReader(inputStream, StandardCharsets.UTF_8.newDecoder());
        return new LoadSource(null, reader, INPUT_STREAM_SOURCE_NAME, "inputStream");
    }

    /**
     * File path for path sources; {@code null} for stream sources.
     */
    public Path path() {
        return path;
    }

    /**
     * Diagnostic name: the file path, or a synthetic {@code <reader>} /
     * {@code <input-stream>} marker for stream sources.
     */
    public String name() {
        return name;
    }

    public boolean isMissing() {
        return path == null && reader == null;
    }

    public String missingMessage() {
        return argumentName + " must not be null";
    }

    public boolean isReadable() {
        return reader != null || (path != null && Files.isRegularFile(path));
    }

    /**
     * Opens a buffered reader over the source. Closing it closes a path source
     * but leaves a caller-owned stream open.
     */
    public BufferedReader openReader() throws IOException {
        if (path != null) {
            return Files.newBufferedReader(path, StandardCharsets.UTF_8);
        }
        return new BufferedReader(new NonClosingReader(reader));
    }

    public String readAll() throws IOException {
        StringWriter content = new StringWriter();
        try (BufferedReader source = openReader()) {
            source.transferTo(content);
        }
        return content.toString();
    }

    /**
     * Describes the source for I/O failure messages, for example
     * {@code CSV file 'rows.csv'} or {@code JSON source <reader>}.
     */
    public String describe(String formatLabel) {
        return path != null
                ? formatLabel + " file '" + path + "'"
                : formatLabel + " source " + name;
    }

    private static final class NonClosingReader extends FilterReader {

        private NonClosingReader(Reader in) {
            super(in);
        }

        @Override
        public void close() {
            // Caller-owned stream: the loader reads it but does not close it.
        }
    }
}
