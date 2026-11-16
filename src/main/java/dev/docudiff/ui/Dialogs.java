package dev.docudiff.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

/**
 * The dialogs every action class shares: ask for a name, choose from a list, confirm a
 * destructive step, and show a warning.
 *
 * <p>Kept separate from the action classes so each of those stays small and readable, and so the
 * wording of a confirmation lives in one place instead of three.
 */
final class Dialogs {

    private Dialogs() {
    }

    static Optional<String> askForName(
            Stage stage, String title, String header, String contentLabel, String suggested) {
        TextInputDialog dialog = new TextInputDialog(suggested);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(contentLabel);
        dialog.initOwner(stage);
        return dialog.showAndWait()
                .map(String::trim)
                .filter(text -> !text.isBlank());
    }

    static Optional<String> choose(
            Stage stage, String title, String header, String contentLabel, List<String> choices) {
        if (choices.isEmpty()) {
            return Optional.empty();
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(contentLabel);
        dialog.initOwner(stage);
        return dialog.showAndWait();
    }

    /** True only when the user presses the confirming button. */
    static boolean confirm(Stage stage, String title, String header, String body) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, body, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.initOwner(stage);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    static void warn(Stage stage, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
