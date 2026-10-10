package se233.project1.task;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import se233.project1.model.ConversionSettings;
import se233.project1.model.ImageItem;
import se233.project1.service.ColorAnalysisService;
import se233.project1.service.ConversionPipeline;
import se233.project1.util.ErrorMessages;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Preprocessing right after the images are loaded: for every image detect its colors, configure
 * Medium detail + 2 colors (the spec defaults), trace it and render the result so that it is shown immediately.
 * <p>
 * The work is distributed by {@link ExecutionPolicy#decideForPreprocessing(int)}: with several files the
 * defaults are Medium + Custom, so the images are processed in parallel. Progress and the thread mode are
 * published through {@link #progressProperty()} / {@link #messageProperty()}.
 * <p>
 * {@code onOutcome} is always invoked on the JavaFX Application Thread, once per image, in completion order.
 */
public final class PreprocessTask extends Task<Void> {

    /** Everything the UI needs to apply to one {@link ImageItem}. Failure when {@code error != null}. */
    public record ItemOutcome(ImageItem item, ConversionSettings settings, int width, int height,
                              int maxColors, String svg, Image original, Image preview, String error) {

        static ItemOutcome failure(ImageItem item, String message) {
            return new ItemOutcome(item, null, 0, 0, 0, null, null, null, message);
        }
    }

    private record Input(ImageItem item, Path path) {
    }

    private final ConversionPipeline pipeline;
    private final List<Input> inputs = new ArrayList<>();
    private final Consumer<ItemOutcome> onOutcome;
    private final ExecutionPolicy.Decision decision;

    /** Must be constructed on the FX thread (it reads the items' source paths). */
    public PreprocessTask(ConversionPipeline pipeline, List<ImageItem> items, Consumer<ItemOutcome> onOutcome) {
        this.pipeline = pipeline;
        this.onOutcome = onOutcome;
        for (ImageItem item : items) {
            inputs.add(new Input(item, item.getSourcePath()));
        }
        this.decision = ExecutionPolicy.decideForPreprocessing(items.size());
    }

    public ExecutionPolicy.Decision getDecision() {
        return decision;
    }

    @Override
    protected Void call() throws Exception {
        final int total = inputs.size();
        updateProgress(0, total);
        updateMessage("Preprocessing 0 / " + total + "  •  " + modeText());

        pipeline.ensureToolAvailable();

        final int[] done = {0};
        BatchProcessor.run(inputs, decision, this::process, completed -> {
            done[0]++;
            ItemOutcome outcome = completed.isSuccess()
                    ? completed.result()
                    : ItemOutcome.failure(completed.input().item(), ErrorMessages.describe(completed.error()));
            Platform.runLater(() -> onOutcome.accept(outcome));
            updateProgress(done[0], total);
            updateMessage("Preprocessing " + done[0] + " / " + total + "  •  " + modeText());
        });
        return null;
    }

    /** Runs on a worker thread; touches no JavaFX node or property. */
    private ItemOutcome process(Input input) throws Exception {
        BufferedImage image = pipeline.loadImage(input.path());
        ColorAnalysisService.ColorAnalysis colors = pipeline.analyze(image);
        ConversionSettings settings = ConversionSettings.defaults().clampedTo(colors.maxCustomColors());
        String svg = pipeline.trace(image, settings);
        Image original = SwingFXUtils.toFXImage(image, null);
        Image preview = pipeline.renderPreview(svg, image.getWidth(), image.getHeight());
        return new ItemOutcome(input.item(), settings, image.getWidth(), image.getHeight(),
                colors.maxCustomColors(), svg, original, preview, null);
    }

    private String modeText() {
        return decision.isParallel() ? "parallel × " + decision.threadCount() + " threads" : "sequential";
    }
}
