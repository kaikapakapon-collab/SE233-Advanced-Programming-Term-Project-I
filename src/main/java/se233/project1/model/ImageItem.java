package se233.project1.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One image in the queue together with its own configuration and latest vector result.
 * <p>
 * Thread-confinement: all properties must be read/written on the JavaFX Application Thread.
 * Background tasks return plain values and the controller applies them via {@code Platform.runLater}.
 */
public final class ImageItem {

    public enum Status { QUEUED, PROCESSING, READY, FAILED }

    private final Path sourcePath;
    private final String displayName;

    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.QUEUED);
    private final ObjectProperty<ConversionSettings> settings = new SimpleObjectProperty<>(ConversionSettings.defaults());
    private final IntegerProperty maxCustomColors = new SimpleIntegerProperty(ConversionSettings.MAX_CUSTOM_COLORS);
    private final IntegerProperty originalWidth = new SimpleIntegerProperty();
    private final IntegerProperty originalHeight = new SimpleIntegerProperty();

    private final ObjectProperty<Image> originalImage = new SimpleObjectProperty<>();
    private final ObjectProperty<Image> vectorPreview = new SimpleObjectProperty<>();
    private final StringProperty svgContent = new SimpleStringProperty();
    private final ObjectProperty<ConversionSettings> resultSettings = new SimpleObjectProperty<>();
    private final StringProperty errorMessage = new SimpleStringProperty();

    public ImageItem(Path sourcePath) {
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        Path name = sourcePath.getFileName();
        this.displayName = name == null ? sourcePath.toString() : name.toString();
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getDisplayName() {
        return displayName;
    }

    // ---- status ----
    public ObjectProperty<Status> statusProperty() { return status; }
    public Status getStatus() { return status.get(); }
    public void setStatus(Status value) { status.set(value); }

    // ---- settings ----
    public ObjectProperty<ConversionSettings> settingsProperty() { return settings; }
    public ConversionSettings getSettings() { return settings.get(); }
    public void setSettings(ConversionSettings value) { settings.set(Objects.requireNonNull(value)); }

    // ---- auto-detected color cap (1..5) ----
    public IntegerProperty maxCustomColorsProperty() { return maxCustomColors; }
    public int getMaxCustomColors() { return maxCustomColors.get(); }

    // ---- original size ----
    public IntegerProperty originalWidthProperty() { return originalWidth; }
    public IntegerProperty originalHeightProperty() { return originalHeight; }
    public int getOriginalWidth() { return originalWidth.get(); }
    public int getOriginalHeight() { return originalHeight.get(); }

    // ---- previews ----
    public ObjectProperty<Image> originalImageProperty() { return originalImage; }
    public Image getOriginalImage() { return originalImage.get(); }
    public void setOriginalImage(Image image) { originalImage.set(image); }

    public ObjectProperty<Image> vectorPreviewProperty() { return vectorPreview; }
    public Image getVectorPreview() { return vectorPreview.get(); }
    public void setVectorPreview(Image image) { vectorPreview.set(image); }

    // ---- result ----
    public StringProperty svgContentProperty() { return svgContent; }
    public String getSvgContent() { return svgContent.get(); }

    public ObjectProperty<ConversionSettings> resultSettingsProperty() { return resultSettings; }

    public StringProperty errorMessageProperty() { return errorMessage; }
    public String getErrorMessage() { return errorMessage.get(); }

    /**
     * Applies the outcome of image analysis: stores the dimensions and the auto-detected color cap
     * and resets the settings to the spec defaults (Medium, Custom, 2 colors, clamped to the cap).
     */
    public void applyAnalysis(int width, int height, int detectedMaxColors) {
        originalWidth.set(width);
        originalHeight.set(height);
        int cap = Math.max(ConversionSettings.MIN_CUSTOM_COLORS,
                Math.min(ConversionSettings.MAX_CUSTOM_COLORS, detectedMaxColors));
        maxCustomColors.set(cap);
        settings.set(ConversionSettings.defaults().clampedTo(cap));
    }

    /** Stores a successful trace together with the settings that produced it. */
    public void setResult(String svg, ConversionSettings usedSettings) {
        svgContent.set(svg);
        resultSettings.set(usedSettings);
        errorMessage.set(null);
        status.set(Status.READY);
    }

    public void markFailed(String message) {
        errorMessage.set(message);
        status.set(Status.FAILED);
    }

    /** True when the stored SVG was generated with exactly the current settings (export can reuse it). */
    public boolean isResultCurrent() {
        return svgContent.get() != null && getSettings().equals(resultSettings.get());
    }

    @Override
    public String toString() {
        return displayName;
    }
}
