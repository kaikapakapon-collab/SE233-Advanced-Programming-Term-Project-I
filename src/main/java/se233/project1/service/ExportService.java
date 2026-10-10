package se233.project1.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Chooses output file names and writes SVG files safely. Stateless and thread-safe. */
public final class ExportService {

    /**
     * Plans one target path per source file name ({@code photo.jpg -> photo.svg}).
     * Names are made unique inside the batch ({@code photo_1.svg}); when {@code overwriteExisting}
     * is false they are also made unique against files already in the folder.
     */
    public List<Path> planOutputPaths(List<String> sourceFileNames, Path outputDir, boolean overwriteExisting) {
        Set<String> used = new HashSet<>();
        List<Path> targets = new ArrayList<>(sourceFileNames.size());
        for (String name : sourceFileNames) {
            String base = stripExtension(name);
            if (base.isBlank()) {
                base = "image";
            }
            String candidate = base + ".svg";
            int counter = 1;
            while (used.contains(candidate.toLowerCase(Locale.ROOT))
                    || (!overwriteExisting && Files.exists(outputDir.resolve(candidate)))) {
                candidate = base + "_" + counter++ + ".svg";
            }
            used.add(candidate.toLowerCase(Locale.ROOT));
            targets.add(outputDir.resolve(candidate));
        }
        return targets;
    }

    /** Writes via a ".part" file and renames it, so a failed or cancelled export never leaves a half-written .svg. */
    public void write(Path target, String svg) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.writeString(temp, svg, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
