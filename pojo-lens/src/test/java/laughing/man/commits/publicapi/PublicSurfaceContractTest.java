package laughing.man.commits.publicapi;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class PublicSurfaceContractTest {

    @Test
    public void firstReadDocsShouldNotExposeFluentAsPublicEntryPoint() throws IOException {
        for (Path path : publicEntryDocs()) {
            String text = Files.readString(path);
            assertFalse(containsWord(text, "PojoLensCore"),
                    () -> path + " should not document PojoLensCore as a public entry point");
            assertFalse(containsWord(text, "QueryBuilder"),
                    () -> path + " should not document QueryBuilder as a public entry point");
            assertFalse(containsWord(text, "FluentQueryDefinition"),
                    () -> path + " should not document FluentQueryDefinition as a public entry point");
            assertFalse(text.contains("ReportDefinition.fluent"),
                    () -> path + " should not document fluent reports as a public entry point");
            assertFalse(text.contains("laughing.man.commits.internal"),
                    () -> path + " should not document internal packages as public entry points");
        }
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private static List<Path> publicEntryDocs() {
        Path root = repoRoot();
        return List.of(
                root.resolve("README.md"),
                root.resolve("MIGRATION.md"),
                root.resolve("docs/entry-points.md"),
                root.resolve("docs/usecases.md"),
                root.resolve("docs/reusable-wrappers.md"),
                root.resolve("docs/reports.md"),
                root.resolve("docs/charts.md"),
                root.resolve("docs/computed-fields.md"),
                root.resolve("docs/time-buckets.md"),
                root.resolve("docs/tabular-schema.md"),
                root.resolve("docs/telemetry.md"),
                root.resolve("docs/caching.md"),
                root.resolve("docs/metamodel.md")
        );
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.exists(current.resolve("pojo-lens"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
