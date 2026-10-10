package se233.project1.task;

import se233.project1.model.ConversionSettings;
import se233.project1.service.ConversionPipeline;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * "Latest request wins" scheduler for preview tracing. Scheduling a new preview cancels the previous one.
 * A cancelled or superseded task never calls back. FX-thread confined (call every method on the FX thread;
 * the callbacks are also delivered on the FX thread).
 */
public final class PreviewScheduler {

    private final Executor executor;
    private PreviewTask current;

    public PreviewScheduler(Executor executor) {
        this.executor = executor;
    }

    public void schedule(ConversionPipeline pipeline, Path source, ConversionSettings settings,
                         long debounceMillis, Consumer<PreviewTask.Result> onSuccess,
                         Consumer<Throwable> onFailure) {
        cancelCurrent();
        PreviewTask task = new PreviewTask(pipeline, source, settings, debounceMillis);
        current = task;
        task.setOnSucceeded(e -> {
            if (current != task) {
                return;
            }
            current = null;
            PreviewTask.Result result = task.getValue();
            if (result == null) {
                onFailure.accept(new IllegalStateException("The preview finished without a result."));
            } else {
                onSuccess.accept(result);
            }
        });
        task.setOnFailed(e -> {
            if (current != task) {
                return;
            }
            current = null;
            onFailure.accept(task.getException());
        });
        executor.execute(task);
    }

    /** Cancels the pending / running preview, if any. Its callbacks will not fire. */
    public void cancelCurrent() {
        PreviewTask task = current;
        current = null;
        if (task != null) {
            task.cancel(true);
        }
    }

    public boolean isBusy() {
        return current != null;
    }
}
