package se233.project1.task;

import javafx.concurrent.Task;
import se233.project1.exception.ArchiveException;
import se233.project1.exception.ConversionException;
import se233.project1.service.ImageLoaderService;
import se233.project1.service.ZipExtractionService;
import se233.project1.util.TempFileManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns whatever the user dropped (images and/or zip archives) into a flat list of image files.
 * Zip archives are extracted into a temp folder (Zip Slip safe). Unsupported items become warnings;
 * the task only fails when nothing usable is left.
 */
public final class IngestTask extends Task<IngestTask.Result> {

    public record Result(List<Path> images, List<String> warnings) {
    }

    private final List<Path> inputs;
    private final ZipExtractionService zipService;

    public IngestTask(List<Path> inputs, ZipExtractionService zipService) {
        this.inputs = List.copyOf(inputs);
        this.zipService = zipService;
    }

    @Override
    protected Result call() throws Exception {
        List<Path> images = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();
        final int total = inputs.size();
        updateProgress(0, total);

        for (int i = 0; i < total; i++) {
            if (isCancelled()) {
                break;
            }
            Path input = inputs.get(i);
            String name = input.getFileName() == null ? input.toString() : input.getFileName().toString();
            updateMessage("Reading " + name + "  (" + (i + 1) + " / " + total + ")");

            if (Files.isDirectory(input)) {
                warnings.add("Skipped folder: " + name);
            } else if (ZipExtractionService.isZipFile(input)) {
                final int index = i;
                try {
                    Path destination = TempFileManager.createSubDir("zip-");
                    images.addAll(zipService.extractImages(input, destination,
                            fraction -> updateProgress(index + fraction, total)));
                } catch (ArchiveException e) {
                    warnings.add(e.getMessage());
                }
            } else if (ImageLoaderService.isSupportedImage(input)) {
                Path absolute = input.toAbsolutePath().normalize();
                if (!Files.isRegularFile(absolute)) {
                    warnings.add("File not found: " + name);
                } else if (seen.add(absolute)) {
                    images.add(absolute);
                }
            } else {
                warnings.add("Skipped unsupported file: " + name);
            }
            updateProgress(i + 1, total);
        }

        if (images.isEmpty()) {
            throw new ConversionException(warnings.isEmpty()
                    ? "No supported images were found. Drop .jpg, .png or a .zip containing images."
                    : String.join("\n", warnings));
        }
        return new Result(List.copyOf(images), List.copyOf(warnings));
    }
}
