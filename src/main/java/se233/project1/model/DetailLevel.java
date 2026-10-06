package se233.project1.model;

/**
 * Detail level selectable in the right-hand panel. Each level maps to a set of Potrace
 * parameters plus the size of the working raster and the palette used for "Unlimited" colors.
 */
public enum DetailLevel {

    //             label     turdsize alphamax optTol  maxDim  unlimitedPalette
    LOW("Low",       25,      1.33,    1.0,    512,    8),
    MEDIUM("Medium", 8,       1.00,    0.4,    1024,   16),
    HIGH("High",     2,       0.80,    0.2,    2048,   32);

    private final String displayName;
    private final int turdSize;
    private final double alphaMax;
    private final double optTolerance;
    private final int maxDimension;
    private final int unlimitedPaletteSize;

    DetailLevel(String displayName, int turdSize, double alphaMax, double optTolerance,
                int maxDimension, int unlimitedPaletteSize) {
        this.displayName = displayName;
        this.turdSize = turdSize;
        this.alphaMax = alphaMax;
        this.optTolerance = optTolerance;
        this.maxDimension = maxDimension;
        this.unlimitedPaletteSize = unlimitedPaletteSize;
    }

    public String displayName() {
        return displayName;
    }

    /** Potrace {@code -t}: suppress speckles up to this many pixels. */
    public int turdSize() {
        return turdSize;
    }

    /** Potrace {@code -a}: corner threshold (smaller = more corners = more detail). */
    public double alphaMax() {
        return alphaMax;
    }

    /** Potrace {@code -O}: curve optimization tolerance (smaller = more faithful). */
    public double optTolerance() {
        return optTolerance;
    }

    /** Longest side (px) of the raster handed to the tracer; larger images are down-scaled. */
    public int maxDimension() {
        return maxDimension;
    }

    /** Number of palette colors used to approximate the "Unlimited" (original) palette. */
    public int unlimitedPaletteSize() {
        return unlimitedPaletteSize;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
