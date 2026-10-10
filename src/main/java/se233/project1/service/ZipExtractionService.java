package se233.project1.service;

import se233.project1.exception.ArchiveException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts the JPG/PNG images of a zip archive into a directory.
 * <ul>
 *   <li><b>Zip Slip protection</b>: every entry path is resolved against the destination and
 *       normalized; an entry that escapes the destination aborts the whole extraction.</li>
 *   <li><b>Zip bomb protection</b>: limits on entry count, bytes per entry and total bytes,
 *       enforced while copying (declared sizes in the archive are not trusted).</li>
 *   <li>Ignores directories, macOS {@code __MACOSX} metadata, hidden files and non-image files.</li>
 *   <li>Never overwrites: duplicate names are renamed {@code name_1.png}, {@code name_2.png}...</li>
 * </ul>
 * Safe to call from a background thread; honours thread interruption (throws {@link CancellationException}).
 */
public final class ZipExtractionService {

    public static final int MAX_ENTRIES = 10_000;
    public static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;
    private static final int BUFFER_SIZE = 64 * 1024;

    public static boolean isZipFile(Path file) {
        return file != null && file.getFileName() != null
                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public List<Path> extractImages(Path zipFile, Path destinationDir) throws ArchiveException {
        return extractImages(zipFile, destinationDir, null);
    }

    /**
     * @param zipFile        archive to read
     * @param destinationDir directory to extract into (created if needed)
     * @param progress       optional callback receiving 0.0..1.0 (called on the calling thread)
     * @return extracted image files in natural name order
     * @throws ArchiveException if the archive is corrupted, unsafe, too large or has no images
     */
    public List<Path> extractImages(Path zipFile, Path destinationDir, DoubleConsumer progress)
            throws ArchiveException {
        Objects.requireNonNull(zipFile, "zipFile");
        Objects.requireNonNull(destinationDir, "destinationDir");
        String zipName = zipFile.getFileName() == null ? zipFile.toString() : zipFile.getFileName().toString();

        if (!Files.isRegularFile(zipFile)) {
            throw new ArchiveException("Archive not found: " + zipName);
        }

        Path destRoot;
        try {
            Files.createDirectories(destinationDir);
            destRoot = destinationDir.toRealPath();
        } catch (IOException e) {
            throw new ArchiveException("Cannot prepare extraction folder: " + e.getMessage(), e);
        }

        List<Path> extracted = new ArrayList<>();
        try (ZipFile zip = openZip(zipFile, zipName)) {
            int entryCount = zip.size();
            if (entryCount > MAX_ENTRIES) {
                throw new ArchiveException("Archive '" + zipName + "' has too many entries ("
                        + entryCount + "; limit " + MAX_ENTRIES + ").");
            }

            long totalBytes = 0;
            int processed = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Zip extraction cancelled");
                }
                ZipEntry entry = entries.nextElement();
                processed++;
                if (progress != null && entryCount > 0) {
                    progress.accept((double) processed / entryCount);
                }

                String name = entry.getName().replace('\\', '/');
                // Security check runs for EVERY entry, even ones we would skip afterwards.
                Path target = resolveSafely(destRoot, name);

                if (entry.isDirectory() || shouldSkip(name)) {
                    continue;
                }
                target = uniquePath(target);
                Files.createDirectories(target.getParent());
                totalBytes += copyEntry(zip, entry, target, MAX_TOTAL_BYTES - totalBytes, zipName);
                extracted.add(target);
            }
        } catch (IOException e) {
            throw new ArchiveException("Failed to extract '" + zipName + "': " + e.getMessage(), e);
        }

        if (extracted.isEmpty()) {
            throw new ArchiveException("Archive '" + zipName + "' contains no .jpg / .jpeg / .png images.");
        }
        extracted.sort(Comparator.comparing((Path p) -> p.getFileName().toString().toLowerCase(Locale.ROOT),
                ZipExtractionService::naturalCompare));
        return extracted;
    }

    private static ZipFile openZip(Path file, String zipName) throws ArchiveException {
        List<Charset> charsets = new ArrayList<>();
        charsets.add(StandardCharsets.UTF_8);
        if (Charset.isSupported("windows-874")) {
            charsets.add(Charset.forName("windows-874")); // Thai file names zipped on Windows
        }
        charsets.add(StandardCharsets.ISO_8859_1);

        Exception last = null;
        for (Charset cs : charsets) {
            try {
                return new ZipFile(file.toFile(), cs);
            } catch (IOException | IllegalArgumentException e) {
                last = e;
            }
        }
        throw new ArchiveException("'" + zipName + "' is not a valid zip archive or is corrupted.", last);
    }

    private static Path resolveSafely(Path destRoot, String entryName) throws ArchiveException {
        try {
            Path resolved = destRoot.resolve(entryName).normalize();
            if (!resolved.startsWith(destRoot)) {
                throw new ArchiveException("Blocked unsafe path in archive (Zip Slip attempt): " + entryName);
            }
            return resolved;
        } catch (InvalidPathException e) {
            throw new ArchiveException("Invalid entry name in archive: " + entryName, e);
        }
    }

    private static boolean shouldSkip(String entryName) {
        if (entryName.startsWith("__MACOSX/") || entryName.contains("/__MACOSX/")) {
            return true;
        }
        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
        if (fileName.startsWith(".")) {
            return true;
        }
        return !ImageLoaderService.hasSupportedExtension(fileName);
    }

    private static Path uniquePath(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int counter = 1;
        Path candidate;
        do {
            candidate = target.resolveSibling(base + "_" + counter++ + ext);
        } while (Files.exists(candidate));
        return candidate;
    }

    private static long copyEntry(ZipFile zip, ZipEntry entry, Path target, long remainingTotal, String zipName)
            throws IOException, ArchiveException {
        long limit = Math.min(MAX_ENTRY_BYTES, remainingTotal);
        long written = 0;
        boolean success = false;
        try (InputStream in = zip.getInputStream(entry);
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                written += n;
                if (written > limit) {
                    throw new ArchiveException("Archive '" + zipName
                            + "' exceeds the allowed extracted size (possible zip bomb).");
                }
                out.write(buffer, 0, n);
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Zip extraction cancelled");
                }
            }
            success = true;
        } finally {
            if (!success) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
        return written;
    }

    /** Compares strings so that "img2" sorts before "img10". */
    static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                if (na.length() != nb.length()) {
                    return Integer.compare(na.length(), nb.length());
                }
                int cmp = na.compareTo(nb);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
