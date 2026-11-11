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
            String searchTerm = System.getProperty("search.term");
            if (searchTerm != null) {
                sleep(2500);
                Platform.runLater(() -> typeSearch(scene, searchTerm));
            }
            if (Boolean.getBoolean("show.help")) {
                sleep(2500);
                Platform.runLater(() -> DiffLegend.showHelpDialog(stage));
                System.out.println("HARNESS help dialog opened");
            }
            if (Boolean.getBoolean("dump.ui")) {
                // Long enough for the background comparison to finish: the diff is computed off
                // the JavaFX thread, and dumping 800ms in caught a still-running task on the first
                // open, which reads as "the header never updated".
                sleep(3000);
                Platform.runLater(() -> dumpUi(scene));
            }
            if (Boolean.getBoolean("show.deleted")) {
                sleep(1200);
                Platform.runLater(() -> toggleShowDeleted(scene));
            }
            sleep(holdSeconds * 1000L);
            Platform.exit();
        }).start();
    }

    /**
     * Prints the state of the widgets added in v1: the comparison header, the export button, the
     * unsupported-construct notices, the toolbar's More menu, and the version list.
     *
     * <p>Values come from the widgets, not from a screenshot: an image pass reports a plausible
     * string, and these are exactly the strings worth being sure about.
     */
    private static void dumpUi(Scene scene) {
        for (Node node : scene.getRoot().lookupAll(".comparison-sides")) {
            if (node instanceof javafx.scene.control.Label label) {
                System.out.println("HARNESS sides = " + label.getText());
            }
        }
        for (Node node : scene.getRoot().lookupAll(".diff-notice")) {
            if (node instanceof javafx.scene.control.Label label) {
                System.out.println("HARNESS notice = " + label.getText());
            }
        }
        for (Node node : scene.getRoot().lookupAll(".button")) {
            if (node instanceof javafx.scene.control.Button button
                    && button.getText() != null
                    && button.getText().startsWith("Export")) {
                System.out.println("HARNESS export button = " + button.getText()
                        + " enabled=" + !button.isDisabled());
            }
            if (node instanceof javafx.scene.control.MenuButton menuButton
                    && "More".equals(menuButton.getText())) {
                System.out.println("HARNESS More items = " + menuButton.getItems().stream()
                        .map(javafx.scene.control.MenuItem::getText)
                        .filter(java.util.Objects::nonNull)
                        .toList());
            }
        }
        Node versionNode = scene.lookup(".list-view");
        for (Node node : scene.getRoot().lookupAll(".list-view")) {
            if (node instanceof ListView<?> listView) {
                System.out.println("HARNESS list(" + listView.getItems().size() + ") = "
                        + listView.getItems());
            }
        }
        if (versionNode != null) {
            System.out.println("HARNESS first list-view found");
        }
        for (Node node : scene.getRoot().lookupAll(".check-box")) {
            if (node instanceof javafx.scene.control.CheckBox checkBox) {
                System.out.println("HARNESS checkbox = \"" + checkBox.getText()
                        + "\" selected=" + checkBox.isSelected());
            }
        }
    }

    private static void toggleShowDeleted(Scene scene) {
        for (Node node : scene.getRoot().lookupAll(".check-box")) {
            if (node instanceof javafx.scene.control.CheckBox checkBox
                    && checkBox.getText() != null
                    && checkBox.getText().contains("Show deleted")) {
                checkBox.fire();
                System.out.println("HARNESS Show deleted -> " + checkBox.isSelected());
                dumpUi(scene);
                return;
            }
        }
        System.out.println("HARNESS Show deleted checkbox not found");
    }

    /** Types into the diff search box, so a screenshot can show highlighting and the note. */
    private static void typeSearch(Scene scene, String term) {
        Node node = scene.lookup(".search-field");
        if (node instanceof javafx.scene.control.TextField field) {
            field.setText(term);
            System.out.println("HARNESS searched for: " + term);
        } else {
            System.out.println("HARNESS search field not found");
        }
        dumpSlots(scene);
    }

    /** Prints what the two slot dropdowns and the library list actually hold, for verification. */
    private static void dumpSlots(Scene scene) {
        int index = 0;
        for (Node node : scene.getRoot().lookupAll(".slot-box")) {
            if (node instanceof javafx.scene.control.ComboBox<?> box) {
                System.out.println("HARNESS slot[" + index + "] = " + box.getValue());
                index++;
            }
        }
        Node listNode = scene.lookup(".library-pane .list-view");
        if (listNode instanceof ListView<?> listView) {
            System.out.println("HARNESS library items = " + listView.getItems());
            System.out.println("HARNESS library selected = "
                    + listView.getSelectionModel().getSelectedItem());
        }
    }

    private static void selectFirstDocument(Scene scene) {
        String wanted = System.getProperty("select.doc");
        Node node = scene.lookup(".library-pane .list-view");
        if (!(node instanceof ListView<?> listView) || listView.getItems().isEmpty()) {
            System.out.println("HARNESS no document found in library");
            return;
        }
        if (wanted != null) {
            for (int index = 0; index < listView.getItems().size(); index++) {
                Object item = listView.getItems().get(index);
                if (item != null && item.toString().contains(wanted)) {
                    listView.getSelectionModel().select(index);
                    System.out.println("HARNESS selected index " + index + " for " + wanted);
                    return;
                }
            }
            System.out.println("HARNESS no match for " + wanted + "; selecting first");
        }
        listView.getSelectionModel().select(0);
        System.out.println("HARNESS selected: " + listView.getItems().get(0));
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
