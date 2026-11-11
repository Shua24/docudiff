package dev.docudiff.ui;

import dev.docudiff.versioning.VersionEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The version list. Newest first, with the snapshot name as the primary line and timestamp as the
 * secondary, because those are the two things a reader needs to tell two versions apart.
 */
public final class VersionListPane extends VBox {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ObservableList<VersionEntry> versions = FXCollections.observableArrayList();
    private final ListView<VersionEntry> listView = new ListView<>(versions);

    public VersionListPane(BiConsumer<VersionEntry, Boolean> selectionHandler) {
        setSpacing(6);
        setPadding(new javafx.geometry.Insets(12, 12, 12, 0));

        Label title = UiParts.sectionHeader("Versions");

        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(VersionEntry version, boolean empty) {
                super.updateItem(version, empty);
                if (empty || version == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(version.name());
                Label detail = UiParts.muted(
                        TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(version.timestamp()))
                                + "  ·  " + version.commitId().substring(0, 7));
                VBox cell = new VBox(2, name, detail);
                setGraphic(cell);
                setText(null);
            }
        });

        listView.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> {
                    if (current != null) {
                        selectionHandler.accept(current, false);
                    }
                });

        getChildren().addAll(title, listView);
        VBox.setVgrow(listView, javafx.scene.layout.Priority.ALWAYS);
    }

    public void setVersions(List<VersionEntry> newVersions) {
        versions.setAll(newVersions);
    }

    public VersionEntry selected() {
        return listView.getSelectionModel().getSelectedItem();
    }

    public void selectFirst() {
        if (!versions.isEmpty()) {
            listView.getSelectionModel().select(0);
        }
    }
}
