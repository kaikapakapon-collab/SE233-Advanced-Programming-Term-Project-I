package se233.project1.model;

/** Colors selector: keep the original palette or limit to a custom number of colors. */
public enum ColorMode {

    UNLIMITED("Unlimited"),
    CUSTOM("Custom");

    private final String displayName;

    ColorMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
