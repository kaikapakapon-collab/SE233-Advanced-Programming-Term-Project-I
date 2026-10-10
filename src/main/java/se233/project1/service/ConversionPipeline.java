package se233.project1.service;

import javafx.scene.image.Image;
import se233.project1.exception.ConversionException;
import se233.project1.exception.ToolNotFoundException;
import se233.project1.model.ConversionSettings;
import se233.project1.util.NativeToolLocator;

import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade over the core services used by every background task:
 * load (with a soft cache) -> analyze colors -> trace -> render preview.
 * Thread-safe; all methods may be called from worker threads.
 */
public final class ConversionPipeline {

    /** The vector preview bitmap is rendered with its longest side clamped to this range. */
    public static final int MIN_PREVIEW_SIDE = 1200;
    public static final int MAX_PREVIEW_SIDE = 3000;

    private final NativeToolLocator locator;
    private final ImageLoaderService loader = new ImageLoaderService();
    private final ColorAnalysisService analysis = new ColorAnalysisService();
    private final TracingService tracing;
    private final SvgRenderService renderer = new SvgRenderService();
    private final Map<Path, SoftReference<BufferedImage>> cache = new ConcurrentHashMap<>();

    public ConversionPipeline(NativeToolLocator locator) {
        this.locator = Objects.requireNonNull(locator, "locator");
        this.tracing = new PotraceTracingService(locator);
    }

    /** Fails fast with a helpful message when Potrace is missing. */
    public void ensureToolAvailable() throws ToolNotFoundException {
        locator.locatePotrace();
    }

    /** Decodes an image, reusing a recently decoded copy while memory allows. */
    public BufferedImage loadImage(Path file) throws ConversionException {
        SoftReference<BufferedImage> ref = cache.get(file);
        BufferedImage image = ref == null ? null : ref.get();
        if (image == null) {
            image = loader.load(file);
            cache.put(file, new SoftReference<>(image));
        }
        return image;
    }

    public ColorAnalysisService.ColorAnalysis analyze(BufferedImage image) {
        return analysis.analyze(image);
    }

    public String trace(BufferedImage image, ConversionSettings settings) throws ConversionException {
        return tracing.trace(image, settings);
    }

    public String traceFile(Path file, ConversionSettings settings) throws ConversionException {
        return trace(loadImage(file), settings);
    }

    /**
     * Rasterizes the SVG for the preview pane. The bitmap resolution is independent of the zoom level:
     * the UI scales the ImageView to the original image size times the zoom factor.
     */
    public Image renderPreview(String svg, int originalWidth, int originalHeight) throws ConversionException {
        int longest = Math.max(1, Math.max(originalWidth, originalHeight));
        int target = Math.min(MAX_PREVIEW_SIDE, Math.max(MIN_PREVIEW_SIDE, longest));
        double scale = (double) target / longest;
        return renderer.renderFx(svg, originalWidth * scale, originalHeight * scale);
    }

    public void clearCache() {
        cache.clear();
    }
}
