package se233.project1.service;

import th.ac.cmu.se233.vectorizer.model.ConversionSettings;
import th.ac.cmu.se233.vectorizer.model.QuantizedImage;

import java.awt.image.BufferedImage;

/**
 * Auto-detects how many colors an image really contains (to size the "Custom" colors selector)
 * and finds the background color of a quantized image (for "Remove background").
 * Stateless and thread-safe.
 */
public final class ColorAnalysisService {

    /** Transparent-pixel ratio above which the image is considered to already have a transparent background. */
    public static final double TRANSPARENCY_THRESHOLD = 0.01;

    private static final int TARGET_SAMPLES = 250_000;
    private static final double SIGNIFICANCE = 0.005;      // a color must cover >= 0.5% of opaque pixels
    private static final int ALPHA_THRESHOLD = 128;
    private static final double BACKGROUND_MIN_BORDER_SHARE = 0.30;

    /**
     * @param significantColors  number of visually significant colors found
     * @param maxCustomColors    upper bound for the Custom selector: min(significantColors, 5), at least 1
     * @param defaultCustomColors pre-selected value: min(2, maxCustomColors)
     * @param transparencyRatio  share of (sampled) pixels that are mostly transparent
     */
    public record ColorAnalysis(int significantColors, int maxCustomColors,
                                int defaultCustomColors, double transparencyRatio) {
    }

    public ColorAnalysis analyze(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int step = Math.max(1, (int) Math.sqrt((double) w * h / TARGET_SAMPLES));

        int[] bins = new int[4096]; // 4 bits per channel
        long opaque = 0;
        long transparent = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int p = image.getRGB(x, y);
                if ((p >>> 24) < ALPHA_THRESHOLD) {
                    transparent++;
                    continue;
                }
                opaque++;
                bins[(((p >> 20) & 0xF) << 8) | (((p >> 12) & 0xF) << 4) | ((p >> 4) & 0xF)]++;
            }
        }

        int significant = 0;
        if (opaque > 0) {
            long threshold = Math.max(1, (long) Math.ceil(opaque * SIGNIFICANCE));
            for (int count : bins) {
                if (count >= threshold) {
                    significant++;
                }
            }
        }
        int max = Math.max(ConversionSettings.MIN_CUSTOM_COLORS,
                Math.min(ConversionSettings.MAX_CUSTOM_COLORS, significant));
        int def = Math.min(ConversionSettings.DEFAULT_CUSTOM_COLORS, max);
        long sampled = opaque + transparent;
        double transparency = sampled == 0 ? 0 : (double) transparent / sampled;
        return new ColorAnalysis(significant, max, def, transparency);
    }

    /**
     * Returns the palette index of the background: the most frequent color along the 1-pixel image
     * border, provided it covers at least 30% of the border. Returns -1 when there is no clear background.
     */
    public int findBackgroundIndex(QuantizedImage image) {
        int w = image.width();
        int h = image.height();
        int[] indices = image.indices();
        long[] counts = new long[image.paletteSize()];
        long total = 0;

        for (int x = 0; x < w; x++) {
            total += tally(indices[x], counts);
            if (h > 1) {
                total += tally(indices[(h - 1) * w + x], counts);
            }
        }
        for (int y = 1; y < h - 1; y++) {
            total += tally(indices[y * w], counts);
            if (w > 1) {
                total += tally(indices[y * w + w - 1], counts);
            }
        }

        int best = -1;
        long bestCount = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                best = i;
            }
        }
        if (best < 0 || total == 0 || (double) bestCount / total < BACKGROUND_MIN_BORDER_SHARE) {
            return -1;
        }
        return best;
    }

    private static int tally(int index, long[] counts) {
        if (index < 0) {
            return 0;
        }
        counts[index]++;
        return 1;
    }
}
