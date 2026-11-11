package dev.docudiff.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Not part of the app. Drives the real window through the flows that involve modal dialogs, to
 * find crashes that only appear when a dialog is open.
 *
 * <p>Scenario is chosen with -Dscenario:
 * <ul>
 *   <li>{@code help-then-exit} — open the help dialog, then exit while it is up</li>
 *   <li>{@code snapshot-cancel} — click Snapshot, cancel the name dialog</li>
 *   <li>{@code help-close} — open the help dialog and close it properly</li>
 * </ul>
 */
public final class CrashProbe extends Application {

    private static final CountDownLatch SHOWN = new CountDownLatch(1);

    @Override
    public void start(Stage stage) {
        String scenario = System.getProperty("scenario", "help-close");

        MainWindow mainWindow = new MainWindow(stage);
        Scene scene = new Scene(mainWindow, 1180, 760);
        scene.getStylesheets().add(
                CrashProbe.class.getResource("/docudiff.css").toExternalForm());
        stage.setTitle("Crash probe");
        stage.setScene(scene);
        if (Boolean.getBoolean("use.safe.exit")) {
            SafeExit.install(stage);
            System.out.println("PROBE SafeExit installed");
        }
        stage.show();
        SHOWN.countDown();
        System.out.println("PROBE scenario=" + scenario);

        new Thread(() -> {
            sleep(1500);
            Platform.runLater(() -> selectFirstDocument(scene));
            sleep(3000);

            switch (scenario) {
                case "help-then-exit" -> {
                    Platform.runLater(() -> {
                        System.out.println("PROBE opening help");
                        DiffLegend.showHelpDialog(stage);
                        System.out.println("PROBE help returned");
                    });
                    sleep(2500);
                    System.out.println("PROBE calling exit with dialog open");
                    if (Boolean.getBoolean("use.safe.exit")) {
                        SafeExit.requestExit(stage);
                    } else {
                        Platform.exit();
                    }
                }
                case "snapshot-cancel" -> {
                    Platform.runLater(() -> {
                        Button snapshot = findButton(scene, "Snapshot version");
                        System.out.println("PROBE clicking snapshot: " + (snapshot != null));
                        if (snapshot != null) {
                            snapshot.fire();
                        }
                    });
                    sleep(3000);
                    Platform.runLater(() -> {
                        System.out.println("PROBE cancelling any open dialog");
                        closeAnyDialog();
                    });
                    sleep(2000);
                    System.out.println("PROBE exiting");
                    Platform.exit();
                }
                case "help-close" -> {
                    Platform.runLater(() -> {
                        System.out.println("PROBE opening help");
                        DiffLegend.showHelpDialog(stage);
                        System.out.println("PROBE help returned");
                    });
                    sleep(2500);
                    System.out.println("PROBE exiting normally");
                    Platform.exit();
                }
                default -> Platform.exit();
            }
        }).start();
    }

    private static Button findButton(Scene scene, String text) {
        for (Node node : scene.getRoot().lookupAll(".button")) {
            if (node instanceof Button button && text.equals(button.getText())) {
                return button;
            }
        }
        return null;
    }

    private static void closeAnyDialog() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && stage.getScene() != null
                    && stage.getScene().getRoot() instanceof DialogPane dialogPane) {
                dialogPane.getButtonTypes().stream().findFirst().ifPresent(buttonType -> {
                    Node button = dialogPane.lookupButton(buttonType);
                    if (button instanceof Button realButton) {
                        realButton.fire();
                    }
                });
            }
        }
        // Fall back to closing every stage that is not the main one.
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window instanceof Stage stage
                    && stage.getTitle() != null && !stage.getTitle().equals("Crash probe")) {
                stage.close();
            }
        }
    }

    private static void selectFirstDocument(Scene scene) {
        Node node = scene.lookup(".library-pane .list-view");
        if (node instanceof ListView<?> listView && !listView.getItems().isEmpty()) {
            listView.getSelectionModel().select(0);
            System.out.println("PROBE selected document");
        } else {
            System.out.println("PROBE no document in library");
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
