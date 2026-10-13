package se233.project1.model;

/**
 * Detail level selectable in the right-hand panel.
 * Each level maps to Potrace parameters, working image size,
 * and the palette used for Unlimited colors.
 */
public enum DetailLevel {

    //             label     turdsize alphamax optTol  maxDim  unlimitedPalette
    LOW("Low",       25,      1.33,    1.0,    512,    16),
    MEDIUM("Medium", 8,       1.00,    0.4,    1024,   32),
    HIGH("High",     2,       0.80,    0.2,    2048,   64);

    private final String displayName;
    private final int turdSize;
    private final double alphaMax;
    private final double optTolerance;
    private final int maxDimension;
    private final int unlimitedPaletteSize;

    DetailLevel(String displayName, int turdSize, double alphaMax,
                double optTolerance, int maxDimension, int unlimitedPaletteSize) {
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

    /** Potrace -t: suppress speckles up to this many pixels. */
    public int turdSize() {
        return turdSize;
    }

    /** Potrace -a: corner threshold. */
    public double alphaMax() {
        return alphaMax;
    }

    /** Potrace -O: curve optimization tolerance. */
    public double optTolerance() {
        return optTolerance;
    }

    /** Longest side of the working raster. */
    public int maxDimension() {
        return maxDimension;
    }

    /** Palette size for Unlimited color mode. */
    public int unlimitedPaletteSize() {
        return unlimitedPaletteSize;
    }

    @Override
    public String toString() {
        return displayName;
    }
}