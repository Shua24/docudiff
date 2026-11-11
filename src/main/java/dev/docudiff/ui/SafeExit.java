package dev.docudiff.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * Shuts the application down without the GTK teardown crash.
 *
 * <p><b>The bug this exists for.</b> Calling {@code Platform.exit()} while a modal dialog is open
 * segfaults the JVM on Linux:
 *
 * <pre>
 * SIGSEGV (0xb) at pc=0x00007f3384000278
 * C  [libglassgtk3.so+0x9de5c]  Java_com_sun_glass_ui_gtk_GtkWindow__1close+0xc
 * j  com.sun.glass.ui.Window.close()
 * j  com.sun.glass.ui.Application.terminate()
 * j  com.sun.javafx.tk.quantum.QuantumToolkit.lambda$exit$0()
 * </pre>
 *
 * Reproduced deterministically by opening the help dialog and then exiting: the dialog owns a
 * nested event loop, and the toolkit tears down its native window while that loop is still
 * running. Since every dialog in this app uses {@code showAndWait()}, a user closing the window
 * with a dialog open would hit it.
 *
 * <p>The fix is to take exit away from the toolkit's implicit path: turn implicit exit off, close
 * every dialog first, then exit from {@code Platform.runLater} so the current event finishes and
 * the nested loops have unwound.
 */
public final class SafeExit {

    private SafeExit() {
    }

    public static void install(Stage primaryStage) {
        Platform.setImplicitExit(false);
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            requestExit(primaryStage);
        });
    }

    /** Closes any open dialogs, then exits once the dispatch queue has drained. */
    public static void requestExit(Stage primaryStage) {
        closeDialogs(primaryStage);
        primaryStage.close();
        Platform.runLater(Platform::exit);
    }

    private static void closeDialogs(Stage primaryStage) {
        List<Window> windows = List.copyOf(Window.getWindows());
        for (Window window : windows) {
            if (window == primaryStage || !window.isShowing()) {
                continue;
            }
            if (window instanceof Stage dialogStage) {
                closeDialogStage(dialogStage);
            }
        }
    }

    /*
     * A dialog opened with showAndWait() runs a nested event loop. Firing its cancel button lets
     * that loop unwind normally; calling close() on it directly is what the toolkit mishandles.
     */
    private static void closeDialogStage(Stage dialogStage) {
        if (dialogStage.getScene() != null
                && dialogStage.getScene().getRoot() instanceof DialogPane dialogPane) {
            dialogPane.getButtonTypes().stream().findFirst().ifPresent(buttonType -> {
                Node button = dialogPane.lookupButton(buttonType);
                if (button instanceof Button realButton) {
                    realButton.fire();
                }
            });
        }
    }
}
