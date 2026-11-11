package dev.docudiff.ui;

import dev.docudiff.versioning.VersionEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The version list. Newest first, with the snapshot name as the primary line and timestamp as the
 * secondary, because those are the two things a reader needs to tell two versions apart.
 *
 * <p>Deleted versions are hidden by default. The exception is a deleted version a branch still
 * points at: hiding it would leave the branch attached to a version the user cannot see, so it
 * stays listed, marked, with a Restore button. That is exactly the case "I snapshotted by
 * mistake" produces, and refusing to delete it would make the most common deletion impossible.
 */
public final class VersionListPane extends VBox {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ObservableList<VersionEntry> visibleVersions = FXCollections.observableArrayList();
    private final ListView<VersionEntry> listView = new ListView<>(visibleVersions);
    private final CheckBox showDeletedBox = new CheckBox("Show deleted");

    private final BiConsumer<VersionEntry, Boolean> selectionHandler;
    private final Consumer<VersionEntry> restoreHandler;

    private List<VersionEntry> allVersions = List.of();
    private Set<String> branchHeadCommitIds = Set.of();

    public VersionListPane(
            BiConsumer<VersionEntry, Boolean> selectionHandler,
            Consumer<VersionEntry> restoreHandler) {
        this.selectionHandler = selectionHandler;
        this.restoreHandler = restoreHandler;

        setSpacing(6);
        setPadding(new javafx.geometry.Insets(12, 12, 12, 0));

        Label title = UiParts.sectionHeader("Versions");
        showDeletedBox.setOnAction(event -> applyFilter());
        showDeletedBox.setTooltip(new javafx.scene.control.Tooltip(
                "Deleted versions stay in the repository and can be restored"));

        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(VersionEntry version, boolean empty) {
                super.updateItem(version, empty);
                if (empty || version == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setGraphic(cellContent(version));
                setText(null);
            }
        });

        listView.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> {
                    if (current != null) {
                        selectionHandler.accept(current, false);
                    }
                });

        getChildren().addAll(title, showDeletedBox, listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
    }

    private VBox cellContent(VersionEntry version) {
        Label name = new Label(
                version.deleted() ? version.name() + "  (deleted)" : version.name());
        Label detail = UiParts.muted(
                TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(version.timestamp()))
                        + "  ·  " + version.commitId().substring(0, 7));
        VBox labels = new VBox(2, name, detail);

        if (!version.deleted()) {
            return labels;
        }
        Button restoreButton = new Button("Restore");
        restoreButton.setOnAction(event -> restoreHandler.accept(version));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(6, UiParts.muted("held by a branch"), spacer, restoreButton);
        return new VBox(4, labels, actions);
    }

    /**
     * Replaces the list. Branch head ids decide which deleted versions stay visible with the
     * toggle off.
     */
    public void setVersions(List<VersionEntry> versions, Set<String> branchHeadCommitIds) {
        this.allVersions = List.copyOf(versions);
        this.branchHeadCommitIds = Set.copyOf(branchHeadCommitIds);
        applyFilter();
    }

    private void applyFilter() {
        boolean showAll = showDeletedBox.isSelected();
        List<VersionEntry> filtered = allVersions.stream()
                .filter(version -> showAll
                        || !version.deleted()
                        || branchHeadCommitIds.contains(version.commitId()))
                .toList();
        VersionEntry selected = listView.getSelectionModel().getSelectedItem();
        visibleVersions.setAll(filtered);
        if (selected == null) {
            return;
        }
        for (VersionEntry candidate : filtered) {
            if (candidate.commitId().equals(selected.commitId())) {
                listView.getSelectionModel().select(candidate);
                return;
            }
        }
    }

    public VersionEntry selected() {
        return listView.getSelectionModel().getSelectedItem();
    }

    public void selectFirst() {
        if (!visibleVersions.isEmpty()) {
            listView.getSelectionModel().select(0);
        }
    }
}
