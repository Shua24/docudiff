package dev.docudiff;

import dev.docudiff.render.OfficeLocator;
import dev.docudiff.ui.MainWindow;
import dev.docudiff.ui.SafeExit;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Builds the window. Keep this class free of a {@code main} method: the process entry point is
 * {@link Launcher}, and moving {@code main} back here reintroduces the "JavaFX runtime components
 * are missing" failure.
 */
public final class DocudiffApplication extends Application {

    @Override
    public void start(Stage stage) {
        MainWindow mainWindow = new MainWindow(stage);
        Scene scene = new Scene(mainWindow, 1180, 760);
        scene.getStylesheets().add(
                DocudiffApplication.class.getResource("/docudiff.css").toExternalForm());

        String officeStatus = OfficeLocator.version()
                .map(version -> " | " + version)
                .orElse(" | LibreOffice not found — page view disabled, diffing works");
        stage.setTitle("Docudiff" + officeStatus);
        stage.setScene(scene);

        // Closing the window while a modal dialog is open crashes the GTK toolkit during
        // Platform.exit() teardown (measured: SIGSEGV in GtkWindow._close). Taking exit into our
        // own hands lets SafeExit close dialogs first.
        SafeExit.install(stage);

        stage.show();
    }
}
