package dev.docudiff.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Not part of the app. Shows the BusyOverlay on its own so its appearance can be inspected
 * without waiting for a real conversion.
 */
public final class OverlayCapture extends Application {

    private static final CountDownLatch SHOWN = new CountDownLatch(1);

    @Override
    public void start(Stage stage) {
        Label behind = new Label("Window content behind the overlay");
        StackPane root = new StackPane(behind);
        BusyOverlay overlay = new BusyOverlay();
        root.getChildren().add(overlay);

        Scene scene = new Scene(root, 720, 420);
        scene.getStylesheets().add(
                OverlayCapture.class.getResource("/docudiff.css").toExternalForm());
        stage.setTitle("Overlay capture");
        stage.setScene(scene);
        stage.show();

        overlay.show("Making snapshot “quarterly-report-snapshot-2026-11-11”…");
        System.out.println("OVERLAY visible=" + overlay.isVisible());
        SHOWN.countDown();

        new Thread(() -> {
            sleep(12000);
            Platform.exit();
        }).start();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
