package se233.project1.service;

import se233.project1.exception.CorruptedImageException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Reads and validates JPG / PNG files. */
public final class ImageLoaderService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    /** True when the file name ends with .jpg, .jpeg or .png (case-insensitive). */
    public static boolean hasSupportedExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static boolean isSupportedImage(Path file) {
        return file != null && file.getFileName() != null
                && hasSupportedExtension(file.getFileName().toString());
    }

    /**
     * Decodes the whole image.
     *
     * @throws CorruptedImageException if the file is missing, empty, unreadable or not a decodable image
     */
    public BufferedImage load(Path file) throws CorruptedImageException {
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        try {
            if (!Files.isRegularFile(file)) {
                throw new CorruptedImageException("File not found: " + name);
            }
            if (Files.size(file) == 0) {
                throw new CorruptedImageException("File is empty: " + name);
            }
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                throw new CorruptedImageException("'" + name + "' is corrupted or not a valid JPG/PNG image.");
            }
            if (image.getWidth() < 1 || image.getHeight() < 1) {
                throw new CorruptedImageException("'" + name + "' has invalid dimensions.");
            }
            return image;
        } catch (IOException | RuntimeException e) {
            if (e instanceof CorruptedImageException) {
                throw (CorruptedImageException) e;
            }
            throw new CorruptedImageException("Cannot read '" + name + "': " + e.getMessage(), e);
        }
    }
}
