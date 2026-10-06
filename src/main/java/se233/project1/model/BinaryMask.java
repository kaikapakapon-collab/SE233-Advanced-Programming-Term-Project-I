package se233.project1.model;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1-bit mask packed MSB-first, one row padded to a whole byte (exactly the PBM "P4" raster layout).
 * A set bit means "foreground" (black) which is what Potrace traces.
 *
 * @param width    mask width in pixels
 * @param height   mask height in pixels
 * @param data     packed raster
 * @param onPixels number of foreground pixels
 */
public record BinaryMask(int width, int height, byte[] data, long onPixels) {

    public boolean isEmpty() {
        return onPixels == 0;
    }

    /** Writes the mask as a binary PBM (P4) file, a format Potrace reads natively. */
    public void writePbm(Path file) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
            out.write(("P4\n" + width + " " + height + "\n").getBytes(StandardCharsets.US_ASCII));
            out.write(data);
        }
    }
}
