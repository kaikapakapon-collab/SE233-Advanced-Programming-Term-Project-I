package se233.project1.task;

import javafx.concurrent.Task;
import se233.project1.model.ConversionSettings;
import se233.project1.service.ConversionPipeline;
import se233.project1.service.ExportService;
import se233.project1.util.ErrorMessages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves every image as .svg into the chosen folder.
 * <p>
 * The execution mode comes from {@link ExecutionPolicy#decide(java.util.Collection)}: parallel only when
 * there are several files and every one is Medium detail + Custom colors, otherwise sequential. Images whose
 * preview is already up to date reuse their SVG; the others are traced again with their own settings.
 * Progress and the thread mode are published through {@link #progressProperty()} / {@link #messageProperty()}.
 */
public final class BatchExportTask extends Task<BatchExportTask.Summary> {

    /**
     * Immutable snapshot of one export (built on the FX thread so no worker touches UI properties).
     *
     * @param cachedSvg SVG that already matches {@code settings}, or null if the image must be traced again
     */
    public record Job(Path source, String displayName, ConversionSettings settings, String cachedSvg, Path target) {
    }

    public record Summary(int total, int succeeded, List<String> failures, Path outputDir,
                          ExecutionPolicy.Decision decision) {
    }

    private final ConversionPipeline pipeline;
    private final ExportService exportService;
    private final List<Job> jobs;
    private final Path outputDir;
    private final ExecutionPolicy.Decision decision;

    public BatchExportTask(ConversionPipeline pipeline, ExportService exportService,
                           List<Job> jobs, Path outputDir) {
        this.pipeline = pipeline;
        this.exportService = exportService;
        this.jobs = List.copyOf(jobs);
        this.outputDir = outputDir;
        this.decision = ExecutionPolicy.decide(this.jobs.stream().map(Job::settings).toList());
    }

    public ExecutionPolicy.Decision getDecision() {
        return decision;
    }

    @Override
    protected Summary call() throws Exception {
        final int total = jobs.size();
        updateProgress(0, total);
        updateMessage("Exporting 0 / " + total + "  •  " + modeText());

        if (jobs.stream().anyMatch(job -> job.cachedSvg() == null)) {
            pipeline.ensureToolAvailable();
        }
        Files.createDirectories(outputDir);

        final List<String> failures = new ArrayList<>();
        final int[] counters = {0, 0}; // done, succeeded
        BatchProcessor.run(jobs, decision, this::exportOne, completed -> {
            counters[0]++;
            if (completed.isSuccess()) {
                counters[1]++;
            } else {
                failures.add(completed.input().displayName() + ": " + ErrorMessages.describe(completed.error()));
            }
            updateProgress(counters[0], total);
            updateMessage("Exporting " + counters[0] + " / " + total + "  •  " + modeText());
        });
        return new Summary(total, counters[1], List.copyOf(failures), outputDir, decision);
    }

    /** Runs on a worker thread. */
    private Path exportOne(Job job) throws Exception {
        String svg = job.cachedSvg() != null ? job.cachedSvg() : pipeline.traceFile(job.source(), job.settings());
        exportService.write(job.target(), svg);
        return job.target();
    }

    private String modeText() {
        return decision.isParallel() ? "parallel × " + decision.threadCount() + " threads" : "sequential";
    }
}
