package se233.project1.task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Runs one worker function over many inputs using the thread count chosen by {@link ExecutionPolicy}
 * (1 thread = sequential, N threads = parallel). Results are reported on the <em>calling</em> thread in
 * completion order, so callers can update progress without any extra synchronisation.
 * <p>
 * Cancellation: interrupting the calling thread (what {@code Task.cancel(true)} does) makes
 * {@link #run} throw {@link InterruptedException} after shutting the pool down with {@code shutdownNow()},
 * which in turn interrupts every running worker (and kills the Potrace processes they started).
 */
public final class BatchProcessor {

    private BatchProcessor() {
    }

    @FunctionalInterface
    public interface Worker<I, R> {
        R process(I input) throws Exception;
    }

    /** Outcome of one input: either a result or the error that stopped it. */
    public record Completed<I, R>(I input, R result, Throwable error) {
        public boolean isSuccess() {
            return error == null;
        }
    }

    public static <I, R> List<Completed<I, R>> run(List<I> inputs,
                                                   ExecutionPolicy.Decision decision,
                                                   Worker<I, R> worker,
                                                   Consumer<Completed<I, R>> onEach) throws InterruptedException {
        List<Completed<I, R>> all = new ArrayList<>(inputs.size());
        if (inputs.isEmpty()) {
            return all;
        }
        ExecutorService executor = ExecutionPolicy.newExecutor(decision);
        try {
            CompletionService<Completed<I, R>> completion = new ExecutorCompletionService<>(executor);
            for (I input : inputs) {
                completion.submit(() -> {
                    try {
                        return new Completed<I, R>(input, worker.process(input), null);
                    } catch (CancellationException e) {
                        throw e;
                    } catch (Throwable t) {
                        return new Completed<I, R>(input, null, t);
                    }
                });
            }
            for (int i = 0; i < inputs.size(); i++) {
                Future<Completed<I, R>> future = completion.take();
                Completed<I, R> completed;
                try {
                    completed = future.get();
                } catch (ExecutionException e) {
                    // Only a worker that was interrupted (CancellationException) can end up here.
                    throw new CancellationException("Batch cancelled");
                }
                all.add(completed);
                if (onEach != null) {
                    onEach.accept(completed);
                }
            }
            return all;
        } finally {
            executor.shutdownNow();
        }
    }
}
