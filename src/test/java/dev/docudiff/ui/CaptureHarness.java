package dev.docudiff.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Not part of the app. A capture harness that builds the real MainWindow, selects the first
 * document in the library, optionally switches to the Diff tab, and exits after a fixed delay so
 * a screenshot can be taken of whatever is on screen.
 *
 * <p>Run with -Duser.home pointing at a scratch library so it never sees real documents.
 */
public final class CaptureHarness extends Application {

    private static final CountDownLatch SHOWN = new CountDownLatch(1);

    @Override
    public void start(Stage stage) {
        int holdSeconds = Integer.getInteger("hold.seconds", 25);
        String tab = System.getProperty("show.tab", "Document");

        MainWindow mainWindow = new MainWindow(stage);
        Scene scene = new Scene(mainWindow, 1180, 760);
        scene.getStylesheets().add(
                CaptureHarness.class.getResource("/docudiff.css").toExternalForm());
        stage.setTitle("Docudiff capture");
        stage.setScene(scene);
        stage.show();
        SHOWN.countDown();
        System.out.println("HARNESS shown; holding " + holdSeconds + "s; tab=" + tab);

        new Thread(() -> {
            sleep(1500);
            Platform.runLater(() -> selectFirstDocument(scene));
            sleep(3500);
            Platform.runLater(() -> selectTab(scene, tab));
            if (Boolean.getBoolean("show.help")) {
                sleep(2500);
                Platform.runLater(() -> DiffLegend.showHelpDialog(stage));
                System.out.println("HARNESS help dialog opened");
            }
            sleep(holdSeconds * 1000L);
            Platform.exit();
        }).start();
    }

    private static void selectFirstDocument(Scene scene) {
        Node node = scene.lookup(".library-pane .list-view");
        if (node instanceof ListView<?> listView && !listView.getItems().isEmpty()) {
            listView.getSelectionModel().select(0);
            System.out.println("HARNESS selected: " + listView.getItems().get(0));
        } else {
            System.out.println("HARNESS no document found in library");
        }
    }

    private static void selectTab(Scene scene, String tabName) {
        Node node = scene.lookup(".tab-pane");
        if (node instanceof TabPane tabPane) {
            tabPane.getTabs().stream()
                    .filter(candidate -> candidate.getText().equals(tabName))
                    .findFirst()
                    .ifPresent(candidate -> {
                        tabPane.getSelectionModel().select(candidate);
                        System.out.println("HARNESS tab -> " + tabName);
                    });
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
