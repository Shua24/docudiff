package dev.docudiff.ui;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Runs slow work off the JavaFX thread and calls back on it.
 *
 * <p>This exists because a busy overlay cannot be shown any other way: while a task blocks the
 * JavaFX thread, the spinner does not spin, the window does not repaint, and the overlay either
 * never appears or appears frozen. Moving the work off-thread is what makes the progress
 * indicator mean anything.
 */
interface BackgroundRunner {

    /**
     * @param busyMessage text for the overlay while the work runs
     * @param work        the slow call, executed on a worker thread
     * @param onSuccess   receives the result on the JavaFX thread
     */
    <T> void run(String busyMessage, Callable<T> work, Consumer<T> onSuccess);

    /**
     * Runs work with no result. Provided so callers do not have to return a dummy value.
     */
    default void runVoid(
            String busyMessage,
            ThrowingRunnable work,
            Runnable onSuccess) {
        run(busyMessage, () -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run());
    }

    /** A {@link Runnable} whose body may throw, for use with {@link #runVoid}. */
    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** A runner that does nothing off-thread; used by tests and headless contexts. */
    static BackgroundRunner immediate() {
        return new BackgroundRunner() {
            @Override
            public <T> void run(String busyMessage, Callable<T> work, Consumer<T> onSuccess) {
                try {
                    T result = work.call();
                    if (Platform.isFxApplicationThread()) {
                        onSuccess.accept(result);
                    } else {
                        Platform.runLater(() -> onSuccess.accept(result));
                    }
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
    }

    /** Builds a task ready to hand to a daemon thread. */
    static <T> Task<T> taskOf(Callable<T> work) {
        return new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
    }
}
