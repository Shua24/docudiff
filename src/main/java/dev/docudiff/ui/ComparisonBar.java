package dev.docudiff.ui;

import dev.docudiff.compare.ComparisonSlot;
import dev.docudiff.versioning.DocumentFormat;
import dev.docudiff.versioning.VersionEntry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Two slots, each holding a version, the working file, or a file on disk.
 *
 * <p>This is the whole point of the comparison refactor: the left side does not have to be a
 * version and the right side does not have to be one either. Returning a document from a reviewer
 * is simply "a version on the left, a file on the right", with no branch, no import and no
 * history entry.
 */
public final class ComparisonBar extends HBox {

    /** Called when either slot changes, or when the two are swapped. */
    public interface Listener {
        void slotsChanged();
    }

    /** One entry in a slot's dropdown. */
    record SlotItem(String label, ComparisonSlot slot) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final String CHOOSE_FILE_LABEL = "Choose a file…";

    private final ComboBox<SlotItem> leftBox = new ComboBox<>();
    private final ComboBox<SlotItem> rightBox = new ComboBox<>();
    private final Label leftCaption = new Label("From");
    private final Label rightCaption = new Label("To");

    private Listener listener = () -> { };
    private DocumentFormat filterFormat;
    private List<SlotItem> versionItems = List.of();
    private boolean suppressEvents;

    public ComparisonBar() {
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(6, 10, 6, 10));
        getStyleClass().add("comparison-bar");

        configure(leftBox, "The version or file on the left");
        configure(rightBox, "The version or file on the right");

        Button swapButton = new Button("Swap");
        swapButton.getStyleClass().add("swap-button");
        swapButton.setOnAction(event -> swap());
        swapButton.setTooltip(new javafx.scene.control.Tooltip("Swap the two sides"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(leftCaption, leftBox, swapButton, spacer, rightCaption, rightBox);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** The format every slot must share; a file picker is restricted to it. */
    public void setFilterFormat(DocumentFormat filterFormat) {
        this.filterFormat = filterFormat;
    }

    /**
     * Replaces the version entries in both dropdowns, keeping whatever pair is currently chosen
     * when the versions they refer to still exist. "Choose a file…" is always last, so a file can
     * be picked on either side regardless of whether a document is open.
     */
    public void setVersions(List<VersionEntry> versions) {
        List<SlotItem> items = new ArrayList<>();
        items.add(new SlotItem("Working file (as it is now)",
                new ComparisonSlot.WorkingFileSlot("working file")));
        for (VersionEntry version : versions) {
            items.add(new SlotItem(version.name(), new ComparisonSlot.VersionSlot(
                    version.name(), version.commitId())));
        }
        versionItems = List.copyOf(items);
        repopulate();
    }

    public Optional<ComparisonSlot> leftSlot() {
        SlotItem item = leftBox.getValue();
        return item == null ? Optional.empty() : Optional.of(item.slot());
    }

    public Optional<ComparisonSlot> rightSlot() {
        SlotItem item = rightBox.getValue();
        return item == null ? Optional.empty() : Optional.of(item.slot());
    }

    /** Clears both slots, used when no document is open. */
    public void clear() {
        suppressEvents = true;
        versionItems = List.of();
        leftBox.getItems().setAll(fileItemsOnly());
        rightBox.getItems().setAll(fileItemsOnly());
        leftBox.setValue(null);
        rightBox.setValue(null);
        suppressEvents = false;
    }

    private void repopulate() {
        suppressEvents = true;
        SlotItem previousLeft = leftBox.getValue();
        SlotItem previousRight = rightBox.getValue();

        List<SlotItem> boxItems = new ArrayList<>(versionItems);
        boxItems.add(new SlotItem(CHOOSE_FILE_LABEL, null));
        leftBox.getItems().setAll(boxItems);
        rightBox.getItems().setAll(boxItems);

        // The version list is newest first. Opening a document should compare its last two
        // snapshots without the user touching anything, so "To" defaults to the newest version
        // and "From" to the one before it.
        leftBox.setValue(findEquivalent(previousLeft, Math.min(2, versionItems.size() - 1)));
        rightBox.setValue(findEquivalent(previousRight, Math.min(1, versionItems.size() - 1)));
        suppressEvents = false;
    }

    /**
     * Keeps the previously chosen value when it still exists. The defaults differ per side: the
     * left defaults to the second item (the older version) and the right to the newest, so opening
     * a document compares its last two snapshots without the user touching anything.
     */
    private SlotItem findEquivalent(SlotItem previous, int fallbackIndex) {
        if (previous != null) {
            for (SlotItem item : versionItems) {
                if (item.label().equals(previous.label())) {
                    return item;
                }
            }
            if (previous.slot() instanceof ComparisonSlot.FileSlot) {
                return previous;
            }
        }
        if (versionItems.isEmpty()) {
            return null;
        }
        int clamped = Math.max(0, Math.min(fallbackIndex, versionItems.size() - 1));
        return versionItems.get(clamped);
    }

    private void configure(ComboBox<SlotItem> box, String tooltip) {
        box.setPrefWidth(240);
        box.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        box.getStyleClass().add("slot-box");
        box.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (suppressEvents || newValue == null) {
                return;
            }
            if (CHOOSE_FILE_LABEL.equals(newValue.label())) {
                chooseFileInto(box);
                return;
            }
            listener.slotsChanged();
        });
    }

    /**
     * Opens a file chooser restricted to the format still available on the other side, then puts
     * the chosen file into the slot. A cross-format file is not offered at all, so the refusal
     * cannot be reached by accident.
     */
    private void chooseFileInto(ComboBox<SlotItem> box) {
        DocumentFormat requiredFormat = formatForChooser(box);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a document to compare");
        if (requiredFormat != null) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    requiredFormat.name() + " documents",
                    "*." + requiredFormat.canonicalExtension()));
        }
        Window window = getScene() == null ? null : getScene().getWindow();
        java.io.File chosen = chooser.showOpenDialog(window);
        if (chosen == null) {
            suppressEvents = true;
            box.setValue(previousSelection(box));
            suppressEvents = false;
            return;
        }
        Path path = chosen.toPath();
        DocumentFormat format = DocumentFormat.fromFileName(path.getFileName().toString())
                .orElse(null);
        if (format == null) {
            return;
        }
        SlotItem item = new SlotItem(path.getFileName().toString(),
                new ComparisonSlot.FileSlot(path.getFileName().toString(), path, format));
        suppressEvents = true;
        box.getItems().add(item);
        box.setValue(item);
        suppressEvents = false;
        listener.slotsChanged();
    }

    /**
     * The format the chosen file must have: whatever sits in the other slot. A version or the
     * working file fixes it to the open document's format; a file fixes it to that file's format.
     * Null when nothing constrains it, which is the two-file case with no document open.
     */
    private DocumentFormat formatForChooser(ComboBox<SlotItem> box) {
        ComboBox<SlotItem> otherBox = box == leftBox ? rightBox : leftBox;
        SlotItem otherItem = otherBox.getValue();
        if (otherItem != null && otherItem.slot() instanceof ComparisonSlot.FileSlot fileSlot) {
            return fileSlot.format();
        }
        return filterFormat;
    }

    private SlotItem previousSelection(ComboBox<SlotItem> box) {
        List<SlotItem> items = box.getItems();
        if (items.isEmpty()) {
            return null;
        }
        for (SlotItem item : items) {
            if (item.slot() instanceof ComparisonSlot.WorkingFileSlot) {
                return item;
            }
        }
        return items.get(0);
    }

    private void swap() {
        SlotItem left = leftBox.getValue();
        SlotItem right = rightBox.getValue();
        suppressEvents = true;
        leftBox.setValue(right);
        rightBox.setValue(left);
        suppressEvents = false;
        listener.slotsChanged();
    }

    /** The dropdown entries when no document is open: a file on either side, nothing else. */
    private List<SlotItem> fileItemsOnly() {
        return List.of(new SlotItem(CHOOSE_FILE_LABEL, null));
    }

    /** Re-adds the "choose a file" entry after a document closes. */
    public void resetForNoDocument() {
        clear();
    }
}
