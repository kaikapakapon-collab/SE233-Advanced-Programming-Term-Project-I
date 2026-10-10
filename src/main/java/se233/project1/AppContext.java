package se233.project1;

import se233.project1.model.ImageQueue;
import se233.project1.service.ConversionPipeline;
import se233.project1.service.ExportService;
import se233.project1.service.ZipExtractionService;
import se233.project1.task.PreviewScheduler;
import se233.project1.util.NativeToolLocator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Application-wide singletons shared by the controllers (created once by {@link MainApp}). */
public final class AppContext {

    private final NativeToolLocator locator = new NativeToolLocator();
    private final ConversionPipeline pipeline = new ConversionPipeline(locator);
    private final ZipExtractionService zipService = new ZipExtractionService();
    private final ExportService exportService = new ExportService();
    private final ImageQueue queue = new ImageQueue();
    private final ExecutorService background;
    private final PreviewScheduler previewScheduler;

    public AppContext() {
        AtomicInteger counter = new AtomicInteger(1);
        this.background = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "vectorizer-bg-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        this.previewScheduler = new PreviewScheduler(background);
    }

    public ConversionPipeline pipeline() { return pipeline; }
    public ZipExtractionService zipService() { return zipService; }
    public ExportService exportService() { return exportService; }
    public ImageQueue queue() { return queue; }
    public PreviewScheduler previewScheduler() { return previewScheduler; }

    /** Executor for coordinator tasks (ingest, preprocess, export, preview). Daemon threads. */
    public ExecutorService background() { return background; }

    public void shutdown() {
        previewScheduler.cancelCurrent();
        background.shutdownNow();
        pipeline.clearCache();
    }
}
