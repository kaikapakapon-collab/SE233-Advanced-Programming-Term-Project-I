package se233.project1.service;

import th.ac.cmu.se233.vectorizer.model.BinaryMask;
import th.ac.cmu.se233.vectorizer.model.QuantizedImage;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reduces an image to at most N colors and splits it into binary masks (one per color) for Potrace.
 * <p>
 * Algorithm: 5-bit-per-channel histogram -> median cut -> a few weighted k-means refinement
 * passes. Palette colors are the weighted average of the <em>true</em> pixel colors of each cluster, so
 * flat-color artwork keeps its exact original colors. The resulting palette is sorted by area (largest first).
 * <p>
 * Stateless and thread-safe.
 */
public final class ColorQuantizer {

    private static final int BITS = 5;
    private static final int BIN_COUNT = 1 << (3 * BITS);
    private static final int KMEANS_ITERATIONS = 4;
    private static final int ALPHA_THRESHOLD = 128;

    /**
     * @param source               image to reduce
     * @param maxColors            1..N colors in the result (fewer if the image has fewer distinct colors)
     * @param preserveTransparency if true, pixels with alpha &lt; 128 become index -1 (transparent);
     *                             if false, transparency is composited onto white
     */
    public QuantizedImage quantize(BufferedImage source, int maxColors, boolean preserveTransparency) {
        if (maxColors < 1) {
            throw new IllegalArgumentException("maxColors must be >= 1");
        }
        final int w = source.getWidth();
        final int h = source.getHeight();
        final int[] argb = source.getRGB(0, 0, w, h, null, 0, w);
        final int[] rgb = new int[argb.length];

        long[] count = new long[BIN_COUNT];
        long[] sumR = new long[BIN_COUNT];
        long[] sumG = new long[BIN_COUNT];
        long[] sumB = new long[BIN_COUNT];

        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int a = p >>> 24;
            if (preserveTransparency && a < ALPHA_THRESHOLD) {
                rgb[i] = -1;
                continue;
            }
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            if (!preserveTransparency && a < 255) {
                r = compositeOnWhite(r, a);
                g = compositeOnWhite(g, a);
                b = compositeOnWhite(b, a);
            }
            rgb[i] = (r << 16) | (g << 8) | b;
            int bin = binOf(r, g, b);
            count[bin]++;
            sumR[bin] += r;
            sumG[bin] += g;
            sumB[bin] += b;
        }

        int usedCount = 0;
        for (int bin = 0; bin < BIN_COUNT; bin++) {
            if (count[bin] > 0) {
                usedCount++;
            }
        }
        if (usedCount == 0) {
            int[] indices = new int[argb.length];
            Arrays.fill(indices, -1);
            return new QuantizedImage(w, h, new int[0], indices, new long[0]);
        }
        int[] used = new int[usedCount];
        int u = 0;
        for (int bin = 0; bin < BIN_COUNT; bin++) {
            if (count[bin] > 0) {
                used[u++] = bin;
            }
        }

        // ---- median cut ----
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(used, count));
        while (boxes.size() < maxColors) {
            Box best = null;
            double bestScore = -1;
            for (Box box : boxes) {
                if (box.ids.length < 2) {
                    continue;
                }
                double score = (double) box.total * (box.longestRange() + 1);
                if (score > bestScore) {
                    bestScore = score;
                    best = box;
                }
            }
            if (best == null) {
                break;
            }
            Box[] parts = best.split(count);
            boxes.remove(best);
            boxes.add(parts[0]);
            boxes.add(parts[1]);
        }

        // ---- initial centroids = weighted mean of true colors ----
        int k = boxes.size();
        double[][] centroid = new double[k][3];
        for (int c = 0; c < k; c++) {
            long n = 0;
            double r = 0;
            double g = 0;
            double b = 0;
            for (int id : boxes.get(c).ids) {
                n += count[id];
                r += sumR[id];
                g += sumG[id];
                b += sumB[id];
            }
            centroid[c][0] = r / n;
            centroid[c][1] = g / n;
            centroid[c][2] = b / n;
        }

        // ---- k-means refinement on the histogram bins ----
        double[][] binMean = new double[usedCount][3];
        for (int i = 0; i < usedCount; i++) {
            int id = used[i];
            binMean[i][0] = (double) sumR[id] / count[id];
            binMean[i][1] = (double) sumG[id] / count[id];
            binMean[i][2] = (double) sumB[id] / count[id];
        }
        int[] cluster = new int[usedCount];
        for (int iter = 0; iter <= KMEANS_ITERATIONS; iter++) {
            for (int i = 0; i < usedCount; i++) {
                cluster[i] = nearest(binMean[i], centroid);
            }
            if (iter == KMEANS_ITERATIONS) {
                break; // final assignment done, keep centroids consistent with it below
            }
            recomputeCentroids(used, binMean, cluster, count, centroid);
        }
        recomputeCentroids(used, binMean, cluster, count, centroid);

        int[] binToCluster = new int[BIN_COUNT];
        Arrays.fill(binToCluster, -1);
        for (int i = 0; i < usedCount; i++) {
            binToCluster[used[i]] = cluster[i];
        }

        // ---- map pixels ----
        int[] rawIndices = new int[rgb.length];
        long[] rawAreas = new long[k];
        for (int i = 0; i < rgb.length; i++) {
            int v = rgb[i];
            if (v < 0) {
                rawIndices[i] = -1;
                continue;
            }
            int idx = binToCluster[binOf((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF)];
            rawIndices[i] = idx;
            rawAreas[idx]++;
        }

        // ---- drop empty clusters, sort by area descending ----
        List<Integer> order = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            if (rawAreas[c] > 0) {
                order.add(c);
            }
        }
        order.sort((x, y) -> Long.compare(rawAreas[y], rawAreas[x]));
        int n = order.size();
        int[] oldToNew = new int[k];
        Arrays.fill(oldToNew, -1);
        int[] palette = new int[n];
        long[] areas = new long[n];
        for (int newIdx = 0; newIdx < n; newIdx++) {
            int old = order.get(newIdx);
            oldToNew[old] = newIdx;
            palette[newIdx] = (clamp255(centroid[old][0]) << 16)
                    | (clamp255(centroid[old][1]) << 8)
                    | clamp255(centroid[old][2]);
            areas[newIdx] = rawAreas[old];
        }
        for (int i = 0; i < rawIndices.length; i++) {
            if (rawIndices[i] >= 0) {
                rawIndices[i] = oldToNew[rawIndices[i]];
            }
        }
        return new QuantizedImage(w, h, palette, rawIndices, areas);
    }

    /** Mask of the pixels having exactly palette index {@code colorIndex}. */
    public BinaryMask exactMask(QuantizedImage image, int colorIndex) {
        return buildMask(image, colorIndex, true);
    }

    /**
     * "Stacked" mask: every pixel whose palette index is {@code >= fromIndex}. Painting the layers
     * in palette order, each one over the previous, reproduces the image without hairline gaps between
     * neighbouring colors (the classic problem when tracing disjoint masks).
     */
    public BinaryMask stackedMask(QuantizedImage image, int fromIndex) {
        return buildMask(image, fromIndex, false);
    }

    private static BinaryMask buildMask(QuantizedImage image, int index, boolean exact) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        int w = image.width();
        int h = image.height();
        int bytesPerRow = (w + 7) >> 3;
        byte[] data = new byte[bytesPerRow * h];
        int[] indices = image.indices();
        long on = 0;
        for (int y = 0; y < h; y++) {
            int rowBase = y * bytesPerRow;
            int pixelBase = y * w;
            for (int x = 0; x < w; x++) {
                int v = indices[pixelBase + x];
                boolean set = exact ? v == index : v >= index;
                if (set) {
                    data[rowBase + (x >> 3)] |= (byte) (0x80 >> (x & 7));
                    on++;
                }
            }
        }
        return new BinaryMask(w, h, data, on);
    }

    // ------------------------------------------------------------------

    private static void recomputeCentroids(int[] used, double[][] binMean, int[] cluster,
                                           long[] count, double[][] centroid) {
        int k = centroid.length;
        double[][] sum = new double[k][3];
        long[] weight = new long[k];
        for (int i = 0; i < used.length; i++) {
            long wgt = count[used[i]];
            int c = cluster[i];
            sum[c][0] += binMean[i][0] * wgt;
            sum[c][1] += binMean[i][1] * wgt;
            sum[c][2] += binMean[i][2] * wgt;
            weight[c] += wgt;
        }
        for (int c = 0; c < k; c++) {
            if (weight[c] > 0) {
                centroid[c][0] = sum[c][0] / weight[c];
                centroid[c][1] = sum[c][1] / weight[c];
                centroid[c][2] = sum[c][2] / weight[c];
            }
        }
    }

    private static int nearest(double[] color, double[][] centroids) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int c = 0; c < centroids.length; c++) {
            double dr = color[0] - centroids[c][0];
            double dg = color[1] - centroids[c][1];
            double db = color[2] - centroids[c][2];
            double d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private static int binOf(int r, int g, int b) {
        return ((r >> 3) << (2 * BITS)) | ((g >> 3) << BITS) | (b >> 3);
    }

    private static int compositeOnWhite(int channel, int alpha) {
        return (channel * alpha + 255 * (255 - alpha) + 127) / 255;
    }

    private static int clamp255(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /** A set of histogram bins used by the median-cut step. */
    private static final class Box {
        final int[] ids;
        final long total;
        final int[] min = {31, 31, 31};
        final int[] max = {0, 0, 0};

        Box(int[] ids, long[] count) {
            this.ids = ids;
            long t = 0;
            for (int id : ids) {
                t += count[id];
                for (int axis = 0; axis < 3; axis++) {
                    int v = channel(id, axis);
                    if (v < min[axis]) {
                        min[axis] = v;
                    }
                    if (v > max[axis]) {
                        max[axis] = v;
                    }
                }
            }
            this.total = t;
        }

        int longestAxis() {
            int axis = 0;
            int best = max[0] - min[0];
            for (int a = 1; a < 3; a++) {
                int range = max[a] - min[a];
                if (range > best) {
                    best = range;
                    axis = a;
                }
            }
            return axis;
        }

        int longestRange() {
            int axis = longestAxis();
            return max[axis] - min[axis];
        }

        /** Splits at the weighted median bin along the longest axis. Requires at least 2 bins. */
        Box[] split(long[] count) {
            int axis = longestAxis();
            long[] keys = new long[ids.length];
            for (int i = 0; i < ids.length; i++) {
                keys[i] = ((long) channel(ids[i], axis) << 20) | ids[i];
            }
            Arrays.sort(keys);
            int[] sorted = new int[ids.length];
            for (int i = 0; i < keys.length; i++) {
                sorted[i] = (int) (keys[i] & 0xFFFFF);
            }
            long cumulative = 0;
            int cut = sorted.length - 2;
            for (int i = 0; i < sorted.length; i++) {
                cumulative += count[sorted[i]];
                if (cumulative * 2 >= total) {
                    cut = i;
                    break;
                }
            }
            if (cut > sorted.length - 2) {
                cut = sorted.length - 2; // keep both halves non-empty
            }
            return new Box[]{
                    new Box(Arrays.copyOfRange(sorted, 0, cut + 1), count),
                    new Box(Arrays.copyOfRange(sorted, cut + 1, sorted.length), count)
            };
        }

        private static int channel(int binId, int axis) {
            switch (axis) {
                case 0:  return binId >> (2 * BITS);
                case 1:  return (binId >> BITS) & 31;
                default: return binId & 31;
            }
        }
    }
}
