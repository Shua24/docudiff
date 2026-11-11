package dev.docudiff.ui;

import dev.docudiff.render.OfficeLocator;
import dev.docudiff.settings.AppSettings;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The settings dialog. One field: the LibreOffice executable.
 *
 * <p>Saving an empty field removes the setting, which is how the user asks for automatic detection
 * again. The dialog reports what is currently detected, so a wrong path is noticeable before it is
 * saved rather than after the page view fails to render.
 *
 * <p>A path that is not executable is still saved when non-empty, because a user who knows where
 * LibreOffice will be installed should not be blocked by the check; the status line then says the
 * path was not found. Saving is silent on success and the caller reports it, since a failed save
 * is rare and the dialog has no room for an error box.
 */
final class SettingsDialog extends Dialog<Boolean> {

    private final ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
    private final TextField pathField = new TextField();

    SettingsDialog(Window owner) {
        setTitle("Settings");
        setHeaderText("Where LibreOffice lives");
        initOwner(owner);

        pathField.setText(AppSettings.load().libreOfficePath().map(Path::toString).orElse(""));
        pathField.setPromptText("Leave empty to detect LibreOffice automatically");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseButton = new Button("Browse…");
        browseButton.setOnAction(event -> chooseExecutable());

        HBox pathRow = new HBox(8, pathField, browseButton);
        Label detected = UiParts.muted(describeDetection());
        Label warning = UiParts.muted(describeMissingPath());
        warning.setWrapText(true);

        getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);
        getDialogPane().setContent(new VBox(10, pathRow, detected, warning));
        getDialogPane().setPadding(new Insets(4));

        setResultConverter(buttonType -> buttonType == saveButton && save());
    }

    private boolean save() {
        String path = pathField.getText() == null ? "" : pathField.getText().trim();
        try {
            AppSettings.load().setLibreOfficePath(path);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void chooseExecutable() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the LibreOffice executable");
        OfficeLocator.locate().map(Path::getParent).ifPresent(directory ->
                chooser.setInitialDirectory(directory.toFile()));
        java.io.File chosen = chooser.showOpenDialog(
                getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow());
        if (chosen != null) {
            pathField.setText(chosen.getAbsolutePath());
        }
    }

    private String describeDetection() {
        Optional<Path> detected = OfficeLocator.locate();
        if (detected.isEmpty()) {
            return "Nothing detected. The page view shows a placeholder; diffing still works.";
        }
        return "Detected: " + detected.get() + "  (" + OfficeLocator.version().orElse("unknown")
                + ")";
    }

    private String describeMissingPath() {
        Optional<Path> configured = AppSettings.load().libreOfficePath();
        if (configured.isPresent() && !Files.isExecutable(configured.get())) {
            return "The saved path is not executable: " + configured.get();
        }
        return "";
    }
}
