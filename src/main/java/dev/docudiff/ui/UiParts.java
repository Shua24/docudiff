package dev.docudiff.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Small factories for the labels and headers used across the panes, so the visual language stays
 * consistent without each pane re-inventing it.
 */
public final class UiParts {

    private UiParts() {
    }

    public static Label sectionHeader(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-header");
        return label;
    }

    public static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    public static HBox statusRow(String leftText, String rightText) {
        Label left = new Label(leftText);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label right = new Label(rightText);
        right.getStyleClass().add("muted");
        HBox row = new HBox(8, left, spacer, right);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    public static Label badge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", styleClass);
        return badge;
    }
}
