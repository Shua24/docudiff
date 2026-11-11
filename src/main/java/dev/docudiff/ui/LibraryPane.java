package dev.docudiff.ui;

import dev.docudiff.library.DocumentFolder;
import dev.docudiff.library.LibraryRoot;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The left-hand list of documents in the library.
 */
public final class LibraryPane extends VBox {

    private final ObservableList<DocumentFolder> documents = FXCollections.observableArrayList();
    private final ListView<DocumentFolder> listView = new ListView<>(documents);

    public LibraryPane(LibraryRoot libraryRoot, Consumer<DocumentFolder> selectionHandler) {
        getStyleClass().add("library-pane");
        setSpacing(8);
        setPadding(new javafx.geometry.Insets(12));

        javafx.scene.control.Label title = UiParts.sectionHeader("Library");
        javafx.scene.control.Label pathLabel = UiParts.muted(
                libraryRoot.rootPath().toString());

        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentFolder document, boolean empty) {
                super.updateItem(document, empty);
                if (empty || document == null) {
                    setText(null);
                    return;
                }
                setText(document.documentName());
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> {
                    if (current != null) {
                        selectionHandler.accept(current);
                    }
                });

        getChildren().addAll(title, pathLabel, listView);
        VBox.setVgrow(listView, javafx.scene.layout.Priority.ALWAYS);
        refresh(libraryRoot);
    }

    public void refresh(LibraryRoot libraryRoot) {
        try {
            documents.setAll(libraryRoot.documentFolders().stream()
                    .map(DocumentFolder::new)
                    .toList());
        } catch (Exception exception) {
            documents.clear();
        }
    }

    public void selectByName(String documentName) {
        for (DocumentFolder document : documents) {
            if (document.documentName().equals(documentName)) {
                listView.getSelectionModel().select(document);
                return;
            }
        }
    }

    public DocumentFolder selected() {
        return listView.getSelectionModel().getSelectedItem();
    }
}
