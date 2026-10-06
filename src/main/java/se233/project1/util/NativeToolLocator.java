package se233.project1.util;

import th.ac.cmu.se233.vectorizer.exception.ToolNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Finds a usable Potrace executable. Resolution order:
 * <ol>
 *   <li>system property {@code potrace.path} or environment variable {@code POTRACE_PATH}</li>
 *   <li>binary bundled in the jar under {@code /th/ac/cmu/se233/vectorizer/bin/{windows|macos|linux}/}</li>
 *   <li>{@code PATH} (plus common Homebrew / system directories, because GUI apps on macOS
 *       do not inherit the shell PATH)</li>
 * </ol>
 * Every candidate is verified by running {@code potrace --version}.
 */
public final class NativeToolLocator {

    public static final String OVERRIDE_PROPERTY = "potrace.path";
    public static final String OVERRIDE_ENV = "POTRACE_PATH";
    private static final String RESOURCE_ROOT = "/th/ac/cmu/se233/vectorizer/bin/";

    private Path cached;

    public synchronized Path locatePotrace() throws ToolNotFoundException {
        if (cached != null && Files.isExecutable(cached)) {
            return cached;
        }
        List<String> tried = new ArrayList<>();
        Path found = findOverride(tried);
        if (found == null) {
            found = extractBundled(tried);
        }
        if (found == null) {
            found = findOnPath(tried);
        }
        if (found == null) {
            throw new ToolNotFoundException(buildMessage(tried));
        }
        cached = found;
        return found;
    }

    public boolean isPotraceAvailable() {
        try {
            locatePotrace();
            return true;
        } catch (ToolNotFoundException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------

    private Path findOverride(List<String> tried) {
        String value = System.getProperty(OVERRIDE_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(OVERRIDE_ENV);
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        Path candidate = Paths.get(value.trim());
        if (verify(candidate)) {
            return candidate;
        }
        tried.add("configured path '" + value + "' is not a working potrace executable");
        return null;
    }

    private Path extractBundled(List<String> tried) {
        String resource = RESOURCE_ROOT + osFolder() + "/" + executableName();
        try (InputStream in = NativeToolLocator.class.getResourceAsStream(resource)) {
            if (in == null) {
                tried.add("no bundled binary for this OS (" + resource + ")");
                return null;
            }
            Path dir = TempFileManager.createSubDir("bin-");
            Path target = dir.resolve(executableName());
            Files.copy(in, target);
            target.toFile().setExecutable(true, false);
            if (verify(target)) {
                return target;
            }
            tried.add("bundled binary could not be executed");
        } catch (IOException e) {
            tried.add("failed to extract bundled binary: " + e.getMessage());
        }
        return null;
    }

    private Path findOnPath(List<String> tried) {
        List<String> dirs = new ArrayList<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String d : pathEnv.split(java.io.File.pathSeparator)) {
                if (!d.isBlank()) {
                    dirs.add(d);
                }
            }
        }
        if (!isWindows()) {
            dirs.add("/opt/homebrew/bin");
            dirs.add("/usr/local/bin");
            dirs.add("/usr/bin");
        }
        for (String d : dirs) {
            try {
                Path candidate = Paths.get(d).resolve(executableName());
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate) && verify(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // invalid path entry, keep searching
            }
        }
        tried.add("potrace not found on PATH");
        return null;
    }

    private boolean verify(Path executable) {
        try {
            Process p = new ProcessBuilder(executable.toString(), "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String buildMessage(List<String> tried) {
        StringBuilder sb = new StringBuilder("The Potrace command-line tool could not be found.\n");
        for (String t : tried) {
            sb.append(" - ").append(t).append('\n');
        }
        sb.append("\nInstall it and restart the application:\n")
          .append(" - Windows: download potrace.exe from potrace.sourceforge.net and add it to PATH,\n")
          .append("   or set the POTRACE_PATH environment variable\n")
          .append(" - macOS: brew install potrace\n")
          .append(" - Linux: sudo apt install potrace");
        return sb.toString();
    }

    private static boolean isWindows() {
        return osName().contains("win");
    }

    private static String osFolder() {
        String os = osName();
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        }
        return "linux";
    }

    private static String executableName() {
        return isWindows() ? "potrace.exe" : "potrace";
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
