package se233.project1.service;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import se233.project1.exception.ConversionException;

import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders SVG text to raster images for the "Vectorized Result" preview using Apache Batik.
 * Rendering is CPU-bound: always call these methods from a background thread, then hand the
 * resulting {@link Image} to the UI with {@code Platform.runLater}. Thread-safe (a new transcoder per call).
 */
public final class SvgRenderService {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    /** Hard cap on either side of a rendered bitmap, protects against out-of-memory when zooming. */
    public static final int MAX_RENDER_DIMENSION = 8192;

    private static final Pattern ROOT_TAG = Pattern.compile("<svg\\b[^>]*>", Pattern.DOTALL);
    private static final Pattern WIDTH = Pattern.compile("\\swidth\\s*=\\s*\"([0-9.]+)\\s*([a-zA-Z%]*)\"");
    private static final Pattern HEIGHT = Pattern.compile("\\sheight\\s*=\\s*\"([0-9.]+)\\s*([a-zA-Z%]*)\"");
    private static final Pattern VIEWBOX = Pattern.compile("\\sviewBox\\s*=\\s*\"([^\"]+)\"");

    /** Intrinsic size of an SVG document in CSS pixels. */
    public record SvgSize(double width, double height) {
    }

    public SvgSize readIntrinsicSize(String svg) throws ConversionException {
        Matcher root = ROOT_TAG.matcher(svg);
        if (!root.find()) {
            throw new ConversionException("The generated content is not a valid SVG document.");
        }
        String tag = root.group();
        double w = parseLength(WIDTH.matcher(tag));
        double h = parseLength(HEIGHT.matcher(tag));
        if (w <= 0 || h <= 0) {
            Matcher vb = VIEWBOX.matcher(tag);
            if (vb.find()) {
                String[] parts = vb.group(1).trim().split("[\\s,]+");
                if (parts.length == 4) {
                    try {
                        w = Double.parseDouble(parts[2]);
                        h = Double.parseDouble(parts[3]);
                    } catch (NumberFormatException e) {
                        throw new ConversionException("SVG has an invalid viewBox.", e);
                    }
                }
            }
        }
        if (w <= 0 || h <= 0) {
            throw new ConversionException("Cannot determine the size of the SVG.");
        }
        return new SvgSize(w, h);
    }

    /** Renders at an exact pixel size (clamped to {@link #MAX_RENDER_DIMENSION}); background stays transparent. */
    public BufferedImage renderBuffered(String svg, double width, double height) throws ConversionException {
        int w = clampDimension(width);
        int h = clampDimension(height);

        CapturingTranscoder transcoder = new CapturingTranscoder();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, (float) w);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, (float) h);
        try {
            transcoder.transcode(new TranscoderInput(new StringReader(svg)), new TranscoderOutput());
        } catch (CancellationException e) {
            throw e;
        } catch (TranscoderException | RuntimeException e) {
            throw new ConversionException("Cannot render SVG preview: " + e.getMessage(), e);
        }
        BufferedImage image = transcoder.getImage();
        if (image == null) {
            throw new ConversionException("SVG renderer produced no image.");
        }
        return image;
    }

    public Image renderFx(String svg, double width, double height) throws ConversionException {
        return SwingFXUtils.toFXImage(renderBuffered(svg, width, height), null);
    }

    /** Renders at {@code scale} times the intrinsic size (use for zoom-dependent re-rendering). */
    public Image renderFxScaled(String svg, double scale) throws ConversionException {
        if (scale <= 0) {
            throw new IllegalArgumentException("scale must be > 0");
        }
        SvgSize size = readIntrinsicSize(svg);
        return renderFx(svg, size.width() * scale, size.height() * scale);
    }

    /** Renders so that the whole image fits inside {@code maxWidth x maxHeight} (aspect ratio kept). */
    public Image renderFxFitting(String svg, double maxWidth, double maxHeight) throws ConversionException {
        SvgSize size = readIntrinsicSize(svg);
        double scale = Math.min(maxWidth / size.width(), maxHeight / size.height());
        if (scale <= 0 || Double.isNaN(scale) || Double.isInfinite(scale)) {
            scale = 1.0;
        }
        return renderFx(svg, size.width() * scale, size.height() * scale);
    }

    // ------------------------------------------------------------------

    private static double parseLength(Matcher m) {
        if (!m.find()) {
            return -1;
        }
        double value;
        try {
            value = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        switch (unit) {
            case "":
            case "px":
                return value;
            case "pt":
                return value * 96.0 / 72.0;
            case "pc":
                return value * 16.0;
            case "mm":
                return value * 96.0 / 25.4;
            case "cm":
                return value * 96.0 / 2.54;
            case "in":
                return value * 96.0;
            default:
                return -1; // % / em etc.: fall back to the viewBox
        }
    }

    private static int clampDimension(double value) {
        long v = Math.round(value);
        return (int) Math.max(1, Math.min(MAX_RENDER_DIMENSION, v));
    }

    /** Batik transcoder that keeps the rendered BufferedImage in memory instead of writing a file. */
    private static final class CapturingTranscoder extends ImageTranscoder {
        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput output) {
            this.image = img;
        }

        BufferedImage getImage() {
            return image;
        }
    }
}
