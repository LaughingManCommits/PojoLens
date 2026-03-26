package laughing.man.commits.benchmark;

import org.openjdk.jmh.Main;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JmhRunner {

    private JmhRunner() {
    }

    public static void main(String[] args) throws Exception {
        // Helps JMH forks reconstruct classpath outside Maven's classloader layout.
        System.setProperty("jmh.separateClasspathJAR", "true");
        Main.main(expandAtFiles(args));
    }

    private static String[] expandAtFiles(String[] args) throws Exception {
        List<String> expanded = new ArrayList<>();
        for (String arg : args) {
            if (arg != null && arg.startsWith("@") && arg.length() > 1) {
                Path file = Path.of(arg.substring(1));
                if (!Files.exists(file)) {
                    throw new IllegalArgumentException("Argument file not found: " + file);
                }
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    expanded.add(trimmed);
                }
            } else {
                expanded.add(arg);
            }
        }
        return expanded.toArray(new String[0]);
    }
}

