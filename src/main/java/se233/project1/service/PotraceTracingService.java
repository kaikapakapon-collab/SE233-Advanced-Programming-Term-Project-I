package se233.project1.service;

import se233.project1.exception.ConversionException;
import se233.project1.exception.ToolNotFoundException;
import se233.project1.model.BinaryMask;
import se233.project1.model.ConversionSettings;
import se233.project1.model.DetailLevel;
import se233.project1.model.QuantizedImage;
import se233.project1.util.NativeToolLocator;
import se233.project1.util.TempFileManager;

import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Raster-to-SVG tracing with the Potrace command-line tool.
 * <p>
 * Potrace only understands two-color bitmaps, so the pipeline is:
 * <ol>
 *   <li>down-scale to the detail level's working size</li>
 *   <li>quantize to N colors ({@link ColorQuantizer})</li>
 *   <li>optionally pick the background color and drop it (Remove background)</li>
 *   <li>for each color layer build a <em>stacked</em> mask (all pixels of that color and of every
 *       later color), write it as PBM and run {@code potrace --svg}</li>
 *   <li>merge the layer groups into one SVG, painting layers in order. The first layer is emitted
 *       as a cover {@code <rect>} (not traced) unless the background is removed.</li>
 * </ol>
 * Stateless apart from the cached tool location, therefore thread-safe: several threads may call
 * {@link #trace} concurrently (each call uses its own temp directory and its own Potrace processes).
 */
public final class PotraceTracingService implements TracingService {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static final long TIMEOUT_SECONDS = 120;
    private static final Pattern SVG_VIEWBOX =
            Pattern.compile("<svg\\b[^>]*?viewBox=\"([^\"]+)\"", Pattern.DOTALL);
    private static final Pattern GROUP = Pattern.compile("<g\\b([^>]*)>(.*?)</g>", Pattern.DOTALL);
    private static final Pattern FILL_ATTR = Pattern.compile("fill=\"#[0-9a-fA-F]{6}\"");

    private final NativeToolLocator locator;
    private final ColorQuantizer quantizer = new ColorQuantizer();
    private final ColorAnalysisService analysis = new ColorAnalysisService();

    public PotraceTracingService() {
        this(new NativeToolLocator());
    }

    public PotraceTracingService(NativeToolLocator locator) {
        this.locator = Objects.requireNonNull(locator, "locator");
    }

    private record LayerResult(String viewBox, String groupMarkup) {
    }

    @Override
    public String trace(BufferedImage source, ConversionSettings settings) throws ConversionException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(settings, "settings");

        Path potrace = locator.locatePotrace();
        DetailLevel detail = settings.detail();

        BufferedImage work = downscaleIfNeeded(source, detail.maxDimension());
        checkCancelled();

        QuantizedImage quantized = quantizer.quantize(work, settings.paletteSize(), settings.removeBackground());
        boolean backgroundRemoved = false;
        if (settings.removeBackground()
                && quantized.transparentRatio() < ColorAnalysisService.TRANSPARENCY_THRESHOLD) {
            int bg = analysis.findBackgroundIndex(quantized);
            if (bg >= 0) {
                quantized = quantized.moveToFront(bg);
                backgroundRemoved = true;
            }
        }
        checkCancelled();

        Path tempDir = null;
        try {
            tempDir = TempFileManager.createSubDir("trace-");
            return buildSvg(potrace, tempDir, quantized, backgroundRemoved, detail,
                    source.getWidth(), source.getHeight());
        } catch (IOException e) {
            throw new ConversionException("I/O error while tracing: " + e.getMessage(), e);
        } finally {
            TempFileManager.deleteRecursivelyQuietly(tempDir);
        }
    }

    // ------------------------------------------------------------------

    private String buildSvg(Path potrace, Path tempDir, QuantizedImage q, boolean backgroundRemoved,
                            DetailLevel detail, int originalWidth, int originalHeight)
            throws ConversionException, IOException {

        // Layer 0 is a plain cover rectangle when it spans the whole image (no removed background,
        // no transparent pixels); this avoids tracing a rectangle and keeps the SVG small.
        boolean coverRect = !backgroundRemoved && q.paletteSize() > 0 && !q.hasTransparency();
        int firstTracedLayer = (backgroundRemoved || coverRect) ? 1 : 0;

        List<LayerResult> layers = new ArrayList<>();
        List<Integer> layerColors = new ArrayList<>();
        for (int layer = firstTracedLayer; layer < q.paletteSize(); layer++) {
            checkCancelled();
            BinaryMask mask = quantizer.stackedMask(q, layer);
            if (mask.isEmpty()) {
                continue;
            }
            LayerResult result = traceLayer(potrace, tempDir, mask, q.palette()[layer], layer, detail);
            if (result != null) {
                layers.add(result);
                layerColors.add(q.palette()[layer]);
            }
        }

        String viewBox = "0 0 " + q.width() + " " + q.height();
        for (LayerResult r : layers) {
            if (r.viewBox() != null) {
                viewBox = r.viewBox();
                break;
            }
        }
        String[] vb = viewBox.trim().split("[\\s,]+");

        StringBuilder svg = new StringBuilder(4096);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"")
           .append(originalWidth).append("\" height=\"").append(originalHeight)
           .append("\" viewBox=\"").append(viewBox.trim())
           .append("\" preserveAspectRatio=\"xMidYMid meet\">\n");
        if (coverRect) {
            svg.append("<rect x=\"0\" y=\"0\" width=\"").append(vb.length >= 4 ? vb[2] : q.width())
               .append("\" height=\"").append(vb.length >= 4 ? vb[3] : q.height())
               .append("\" fill=\"").append(hex(q.palette()[0])).append("\"/>\n");
        }
        for (LayerResult r : layers) {
            svg.append(r.groupMarkup()).append('\n');
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    private LayerResult traceLayer(Path potrace, Path tempDir, BinaryMask mask, int rgb, int layer,
                                   DetailLevel detail) throws ConversionException, IOException {
        Path pbm = tempDir.resolve("layer-" + layer + ".pbm");
        Path out = tempDir.resolve("layer-" + layer + ".svg");
        Path log = tempDir.resolve("layer-" + layer + ".log");
        mask.writePbm(pbm);

        String color = hex(rgb);
        List<String> command = new ArrayList<>();
        command.add(potrace.toString());
        command.add("-s");
        command.add("-t");
        command.add(String.valueOf(detail.turdSize()));
        command.add("-a");
        command.add(fmt(detail.alphaMax()));
        command.add("-O");
        command.add(fmt(detail.optTolerance()));
        command.add("-C");
        command.add(color);
        command.add("-o");
        command.add(out.toString());
        command.add(pbm.toString());

        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ToolNotFoundException("Cannot start Potrace (" + potrace + "): " + e.getMessage(), e);
        }
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ConversionException("Potrace timed out after " + TIMEOUT_SECONDS + " seconds.");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new CancellationException("Tracing cancelled");
        }
        int exit = process.exitValue();
        if (exit != 0) {
            throw new ConversionException("Potrace failed (exit code " + exit + "): " + readQuietly(log));
        }

        String svg = Files.readString(out, StandardCharsets.UTF_8);
        Files.deleteIfExists(pbm);

        Matcher group = GROUP.matcher(svg);
        if (!group.find()) {
            throw new ConversionException("Unexpected Potrace output (no <g> element found).");
        }
        String attrs = group.group(1);
        String inner = group.group(2);
        if (!inner.contains("<path")) {
            return null; // nothing survived the speckle filter
        }
        Matcher fill = FILL_ATTR.matcher(attrs);
        if (fill.find()) {
            attrs = fill.replaceFirst(Matcher.quoteReplacement("fill=\"" + color + "\""));
        } else {
            attrs = attrs + " fill=\"" + color + "\"";
        }
        Matcher vb = SVG_VIEWBOX.matcher(svg);
        String viewBox = vb.find() ? vb.group(1) : null;
        return new LayerResult(viewBox, "<g" + attrs + ">" + inner.trim() + "</g>");
    }

    // ------------------------------------------------------------------

    private static BufferedImage downscaleIfNeeded(BufferedImage src, int maxDimension) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxDimension) {
            return src;
        }
        double scale = (double) maxDimension / longest;
        int targetW = Math.max(1, (int) Math.round(w * scale));
        int targetH = Math.max(1, (int) Math.round(h * scale));

        BufferedImage current = src;
        int cw = w;
        int ch = h;
        while (cw / 2 >= targetW && ch / 2 >= targetH) { // halve repeatedly for better quality
            cw /= 2;
            ch /= 2;
            current = resize(current, cw, ch);
        }
        if (cw != targetW || ch != targetH) {
            current = resize(current, targetW, targetH);
        }
        return current;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Tracing cancelled");
        }
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String readQuietly(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? "(no message)" : text;
        } catch (IOException e) {
            return "(no message)";
        }
    }
}
