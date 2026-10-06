package se233.project1.model;

import java.util.Objects;

/**
 * Immutable snapshot of the right-panel configuration for one image.
 *
 * @param detail           Low / Medium / High
 * @param colorMode        Unlimited or Custom
 * @param customColorCount number of colors when {@code colorMode == CUSTOM} (1..5)
 * @param removeBackground output a transparent SVG
 */
public record ConversionSettings(DetailLevel detail,
                                 ColorMode colorMode,
                                 int customColorCount,
                                 boolean removeBackground) {

    public static final int MIN_CUSTOM_COLORS = 1;
    public static final int MAX_CUSTOM_COLORS = 5;
    public static final int DEFAULT_CUSTOM_COLORS = 2;

    public ConversionSettings {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(colorMode, "colorMode");
        if (customColorCount < MIN_CUSTOM_COLORS || customColorCount > MAX_CUSTOM_COLORS) {
            throw new IllegalArgumentException("customColorCount must be between "
                    + MIN_CUSTOM_COLORS + " and " + MAX_CUSTOM_COLORS + " but was " + customColorCount);
        }
    }

    /** Default configuration required by the spec: Medium detail, Custom colors, 2 colors. */
    public static ConversionSettings defaults() {
        return new ConversionSettings(DetailLevel.MEDIUM, ColorMode.CUSTOM, DEFAULT_CUSTOM_COLORS, false);
    }

    public ConversionSettings withDetail(DetailLevel newDetail) {
        return new ConversionSettings(newDetail, colorMode, customColorCount, removeBackground);
    }

    public ConversionSettings withColorMode(ColorMode newMode) {
        return new ConversionSettings(detail, newMode, customColorCount, removeBackground);
    }

    public ConversionSettings withCustomColorCount(int count) {
        return new ConversionSettings(detail, colorMode, count, removeBackground);
    }

    public ConversionSettings withRemoveBackground(boolean remove) {
        return new ConversionSettings(detail, colorMode, customColorCount, remove);
    }

    public boolean usesCustomColors() {
        return colorMode == ColorMode.CUSTOM;
    }

    /** Number of palette colors the quantizer must produce for this configuration. */
    public int paletteSize() {
        return usesCustomColors() ? customColorCount : detail.unlimitedPaletteSize();
    }

    /** Returns a copy whose custom color count does not exceed {@code maxColors} (auto-detected cap). */
    public ConversionSettings clampedTo(int maxColors) {
        int cap = Math.max(MIN_CUSTOM_COLORS, Math.min(MAX_CUSTOM_COLORS, maxColors));
        return customColorCount <= cap ? this : withCustomColorCount(cap);
    }
}
