package se233.project1.task;

import th.ac.cmu.se233.vectorizer.model.ColorMode;
import th.ac.cmu.se233.vectorizer.model.ConversionSettings;
import th.ac.cmu.se233.vectorizer.model.DetailLevel;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single place that decides whether a batch runs in parallel.
 * <p>
 * Project rule: with several files, concurrent processing is allowed <b>only</b> when the settings are
 * <b>Medium detail AND Custom colors (1..5, default 2)</b>. For a batch with per-image settings, every
 * image must satisfy the rule; otherwise the whole batch runs sequentially on one worker thread.
 */
public final class ExecutionPolicy {

    public enum Mode { SEQUENTIAL, PARALLEL }

    /** Upper bound on worker threads: each worker spawns Potrace processes and holds bitmaps in memory. */
    public static final int MAX_THREADS = 8;

    /**
     * @param mode        sequential or parallel
     * @param threadCount number of worker threads to use (1 when sequential)
     * @param reason      human-readable explanation, useful for status text and logs
     */
    public record Decision(Mode mode, int threadCount, String reason) {
        public boolean isParallel() {
            return mode == Mode.PARALLEL;
        }
    }

    private ExecutionPolicy() {
    }

    /** True when these settings satisfy the parallel-execution condition (Medium detail + Custom colors). */
    public static boolean isParallelEligible(ConversionSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return settings.detail() == DetailLevel.MEDIUM
                && settings.colorMode() == ColorMode.CUSTOM
                && settings.customColorCount() >= ConversionSettings.MIN_CUSTOM_COLORS
                && settings.customColorCount() <= ConversionSettings.MAX_CUSTOM_COLORS;
    }

    /** Decision for a batch where each image has its own settings. */
    public static Decision decide(Collection<ConversionSettings> settings) {
        Objects.requireNonNull(settings, "settings");
        int files = settings.size();
        if (files <= 1) {
            return new Decision(Mode.SEQUENTIAL, 1, "Single file: nothing to run in parallel");
        }
        for (ConversionSettings s : settings) {
            if (!isParallelEligible(s)) {
                return new Decision(Mode.SEQUENTIAL, 1,
                        "Parallel mode needs Medium detail and Custom colors (found "
                                + s.detail().displayName() + " / " + s.colorMode().displayName() + ")");
            }
        }
        return parallelFor(files);
    }

    /** Decision for {@code fileCount} files that all share the same settings. */
    public static Decision decide(int fileCount, ConversionSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (fileCount <= 1) {
            return new Decision(Mode.SEQUENTIAL, 1, "Single file: nothing to run in parallel");
        }
        if (!isParallelEligible(settings)) {
            return new Decision(Mode.SEQUENTIAL, 1,
                    "Parallel mode needs Medium detail and Custom colors (found "
                            + settings.detail().displayName() + " / " + settings.colorMode().displayName() + ")");
        }
        return parallelFor(fileCount);
    }

    /**
     * Preprocessing on drop always uses the default configuration (Medium, Custom, 2 colors),
     * so it runs in parallel whenever more than one file was dropped.
     */
    public static Decision decideForPreprocessing(int fileCount) {
        return decide(fileCount, ConversionSettings.defaults());
    }

    /** Creates a daemon-thread pool sized according to the decision. */
    public static ExecutorService newExecutor(Decision decision) {
        Objects.requireNonNull(decision, "decision");
        return Executors.newFixedThreadPool(decision.threadCount(), new WorkerThreadFactory(decision.mode()));
    }

    private static Decision parallelFor(int files) {
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = Math.max(2, Math.min(MAX_THREADS, Math.min(cores, files)));
        return new Decision(Mode.PARALLEL, threads,
                "Medium detail + Custom colors: processing " + files + " files on " + threads + " threads");
    }

    private static final class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);
        private final String prefix;

        WorkerThreadFactory(Mode mode) {
            this.prefix = "vectorizer-" + mode.name().toLowerCase() + "-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
