package se233.project1.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Owns one application-wide temp root that is deleted on JVM shutdown. */
public final class TempFileManager {

    private static final Object LOCK = new Object();
    private static Path root;

    private TempFileManager() {
    }

    public static Path root() throws IOException {
        synchronized (LOCK) {
            if (root == null) {
                Path created = Files.createTempDirectory("se233-vectorizer-");
                root = created;
                Runtime.getRuntime().addShutdownHook(
                        new Thread(() -> deleteRecursivelyQuietly(created), "temp-cleanup"));
            }
            return root;
        }
    }

    /** Creates a fresh uniquely named directory below the temp root. */
    public static Path createSubDir(String prefix) throws IOException {
        return Files.createTempDirectory(root(), prefix);
    }

    public static void deleteRecursivelyQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
