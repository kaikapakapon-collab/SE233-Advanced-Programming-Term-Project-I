package se233.project1.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;

/**
 * Drives the two side-by-side previews (Original / Vectorized Result): zoom in, zoom out, zoom to fit,
 * Ctrl/Cmd + mouse wheel zoom, and synchronised scrolling. Both ImageViews always get exactly the same
 * on-screen size (original pixel size x zoom), so the images line up even though the vector bitmap is
 * rendered at its own resolution.
 * <p>
 * This is a plain helper (not an FXML controller): {@link EditorController} passes in the injected nodes.
 */
public final class PreviewPaneController {

    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 16.0;
    private static final double ZOOM_STEP = 1.25;
    private static final double FIT_PADDING = 16.0;

    private final ScrollPane originalScroll;
    private final ImageView originalView;
    private final ImageView vectorView;
    private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);
    private final BooleanProperty hasContent = new SimpleBooleanProperty(false);

    private boolean fitMode = true;
    private double baseWidth;
    private double baseHeight;

    public PreviewPaneController(ScrollPane originalScroll, ImageView originalView,
                                 ScrollPane vectorScroll, ImageView vectorView) {
        this.originalScroll = originalScroll;
        this.originalView = originalView;
        this.vectorView = vectorView;

        for (ImageView view : new ImageView[]{originalView, vectorView}) {
            view.setPreserveRatio(false);
            view.setSmooth(true);
        }

        // Pan / scroll one pane and the other follows.
        vectorScroll.hvalueProperty().bindBidirectional(originalScroll.hvalueProperty());
        vectorScroll.vvalueProperty().bindBidirectional(originalScroll.vvalueProperty());

        // While in "fit" mode, keep fitting when the window / split divider is resized.
        originalScroll.viewportBoundsProperty().addListener((obs, old, bounds) -> {
            if (fitMode) {
                applyFit();
                layoutViews();
            }
        });
        zoom.addListener((obs, old, value) -> layoutViews());

        originalScroll.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        vectorScroll.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
    }

    public ReadOnlyBooleanProperty hasContentProperty() {
        return hasContent;
    }

    public double getZoom() {
        return zoom.get();
    }

    /**
     * @param original  original image (may be null while it is still loading)
     * @param vector    rendered vector result (may be null)
     * @param baseW     original pixel width, base size for both views
     * @param baseH     original pixel height
     * @param resetView true when this is a different image: zoom back to "fit"
     */
    public void setContent(Image original, Image vector, double baseW, double baseH, boolean resetView) {
        originalView.setImage(original);
        vectorView.setImage(vector);
        baseWidth = baseW;
        baseHeight = baseH;
        hasContent.set(original != null && baseW > 0 && baseH > 0);
        if (resetView) {
            fitMode = true;
        }
        if (fitMode) {
            applyFit();
        }
        layoutViews();
    }

    public void clear() {
        originalView.setImage(null);
        vectorView.setImage(null);
        baseWidth = 0;
        baseHeight = 0;
        hasContent.set(false);
        fitMode = true;
        layoutViews();
    }

    public void zoomIn() {
        if (!hasContent.get()) {
            return;
        }
        fitMode = false;
        zoom.set(clamp(zoom.get() * ZOOM_STEP));
    }

    public void zoomOut() {
        if (!hasContent.get()) {
            return;
        }
        fitMode = false;
        zoom.set(clamp(zoom.get() / ZOOM_STEP));
    }

    public void zoomToFit() {
        if (!hasContent.get()) {
            return;
        }
        fitMode = true;
        applyFit();
        layoutViews();
    }

    // ------------------------------------------------------------------

    private void onScroll(ScrollEvent event) {
        if (event.isShortcutDown() && event.getDeltaY() != 0) {
            if (event.getDeltaY() > 0) {
                zoomIn();
            } else {
                zoomOut();
            }
            event.consume();
        }
    }

    private void applyFit() {
        Bounds viewport = originalScroll.getViewportBounds();
        if (baseWidth <= 0 || baseHeight <= 0 || viewport == null) {
            return;
        }
        double availableW = viewport.getWidth() - 2 * FIT_PADDING;
        double availableH = viewport.getHeight() - 2 * FIT_PADDING;
        if (availableW <= 0 || availableH <= 0) {
            return;
        }
        zoom.set(clamp(Math.min(availableW / baseWidth, availableH / baseHeight)));
    }

    private void layoutViews() {
        double w = baseWidth * zoom.get();
        double h = baseHeight * zoom.get();
        originalView.setFitWidth(w);
        originalView.setFitHeight(h);
        vectorView.setFitWidth(w);
        vectorView.setFitHeight(h);
    }

    private static double clamp(double value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }
}
