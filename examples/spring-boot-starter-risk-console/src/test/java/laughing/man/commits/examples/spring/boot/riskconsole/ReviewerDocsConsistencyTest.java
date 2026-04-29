package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReviewerDocsConsistencyTest {

    private static final Pattern SCREENSHOT_PATTERN = Pattern.compile("([a-z0-9-]+\\.png)");
    private static final Pattern CAPTURE_PATTERN = Pattern.compile("capture\\(\"([a-z0-9-]+\\.png)\"\\)");

    @Test
    void readmeAndReviewerPacketListEveryCapturedScreenshot() throws IOException {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path testRoot = moduleRoot.resolve("src").resolve("test").resolve("java");
        Set<String> capturedScreenshots = new LinkedHashSet<>();
        Files.walk(testRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> capturedScreenshots.addAll(extract(path, CAPTURE_PATTERN)));

        assertFalse(capturedScreenshots.isEmpty(), "Expected at least one captured screenshot");

        Set<String> readmeScreenshots = extract(moduleRoot.resolve("README.md"), SCREENSHOT_PATTERN);
        Set<String> reviewerScreenshots = extract(moduleRoot.resolve("REVIEWER.md"), SCREENSHOT_PATTERN);

        assertEquals(capturedScreenshots, readmeScreenshots, "README screenshot inventory drift");
        assertEquals(capturedScreenshots, reviewerScreenshots, "Reviewer packet screenshot inventory drift");
    }

    private static Set<String> extract(Path path, Pattern pattern) {
        try {
            String content = Files.readString(path);
            Set<String> values = new LinkedHashSet<>();
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                values.add(matcher.group(1));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
