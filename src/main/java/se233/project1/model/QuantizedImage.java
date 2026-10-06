package se233.project1.model;

/**
 * Result of color quantization: every pixel is mapped to a palette index
 * ({@code -1} marks a transparent pixel). The palette is ordered by the producer
 * (area descending by default). Arrays are shared, not copied; treat them as read-only.
 *
 * @param width   image width in pixels
 * @param height  image height in pixels
 * @param palette 0xRRGGBB per color
 * @param indices row-major palette index per pixel, or -1 for transparent
 * @param areas   number of pixels per palette entry
 */
public record QuantizedImage(int width, int height, int[] palette, int[] indices, long[] areas) {

    public int paletteSize() {
        return palette.length;
    }

    public long transparentCount() {
        long opaque = 0;
        for (long a : areas) {
            opaque += a;
        }
        return (long) width * height - opaque;
    }

    public boolean hasTransparency() {
        return transparentCount() > 0;
    }

    public double transparentRatio() {
        long total = (long) width * height;
        return total == 0 ? 0 : (double) transparentCount() / total;
    }

    /** Returns a new image in which palette entry {@code index} becomes entry 0 (others keep their order). */
    public QuantizedImage moveToFront(int index) {
        int n = palette.length;
        if (index < 0 || index >= n) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        if (index == 0) {
            return this;
        }
        int[] oldToNew = new int[n];
        oldToNew[index] = 0;
        int next = 1;
        for (int i = 0; i < n; i++) {
            if (i != index) {
                oldToNew[i] = next++;
            }
        }
        int[] newPalette = new int[n];
        long[] newAreas = new long[n];
        for (int i = 0; i < n; i++) {
            newPalette[oldToNew[i]] = palette[i];
            newAreas[oldToNew[i]] = areas[i];
        }
        int[] newIndices = new int[indices.length];
        for (int p = 0; p < indices.length; p++) {
            int v = indices[p];
            newIndices[p] = v < 0 ? -1 : oldToNew[v];
        }
        return new QuantizedImage(width, height, newPalette, newIndices, newAreas);
    }
}
