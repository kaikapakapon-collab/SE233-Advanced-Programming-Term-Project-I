package se233.project1.task;

import javafx.concurrent.Task;
import javafx.scene.image.Image;
import se233.project1.model.ConversionSettings;
import se233.project1.service.ConversionPipeline;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Re-traces ONE image after the user changed a setting.
 * <p>
 * Debounce: the task first sleeps for {@code debounceMillis} (~300 ms). If the user changes another setting
 * in the meantime the {@link PreviewScheduler} cancels this task; the interrupt wakes the sleep and nothing
 * is traced. Cancelling later interrupts the running Potrace process the same way.
 */
public final class PreviewTask extends Task<PreviewTask.Result> {

    public static final long DEFAULT_DEBOUNCE_MILLIS = 300;

    public record Result(ConversionSettings settings, String svg, Image preview) {
    }

    private final ConversionPipeline pipeline;
    private final Path source;
    private final ConversionSettings settings;
    private final long debounceMillis;

    public PreviewTask(ConversionPipeline pipeline, Path source, ConversionSettings settings, long debounceMillis) {
        this.pipeline = pipeline;
        this.source = source;
        this.settings = settings;
        this.debounceMillis = Math.max(0, debounceMillis);
    }

    @Override
    protected Result call() throws Exception {
        if (debounceMillis > 0) {
            try {
                Thread.sleep(debounceMillis);
            } catch (InterruptedException e) {
                return null; // superseded by a newer request; the task is already in the CANCELLED state
            }
        }
        if (isCancelled()) {
            return null;
        }
        BufferedImage image = pipeline.loadImage(source);
        if (isCancelled()) {
            return null;
        }
        String svg = pipeline.trace(image, settings);
        if (isCancelled()) {
            return null;
        }
        Image preview = pipeline.renderPreview(svg, image.getWidth(), image.getHeight());
        return new Result(settings, svg, preview);
    }
}
