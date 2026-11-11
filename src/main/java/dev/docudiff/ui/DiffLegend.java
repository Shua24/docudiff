package dev.docudiff.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;

/**
 * Explains how to read a diff, in two forms:
 *
 * <ul>
 *   <li>{@link #strip()} — a compact legend that sits permanently under the diff, so the answer is
 *       always on screen without a click.</li>
 *   <li>{@link #showHelpDialog(Window)} — the fuller explanation behind a button, for the first
 *       time someone meets a diff and needs more than colours.</li>
 * </ul>
 *
 * Both are shipped deliberately: the strip is the always-there reminder, the dialog is the
 * never-again-ask reference. Whichever proves more useful, the other can be deleted without
 * touching the diff renderer.
 */
public final class DiffLegend {

    private DiffLegend() {
    }

    /**
     * The always-visible legend strip. Deliberately terse — one line per concept, with the same
     * colours the diff itself uses, so it reads as a key rather than as instructions.
     */
    public static Region strip() {
        FlowPane strip = new FlowPane(18, 6);
        strip.getStyleClass().add("legend-strip");
        strip.setPadding(new Insets(8, 12, 8, 12));
        strip.setAlignment(Pos.CENTER_LEFT);

        Label heading = new Label("Reading this diff:");
        heading.getStyleClass().add("legend-heading");

        strip.getChildren().addAll(
                heading,
                swatchEntry("diff-modified", "warm card = changed text"),
                swatchEntry("diff-added", "green card = new paragraph"),
                swatchEntry("diff-removed", "red card = deleted paragraph"));

        Text removedWord = new Text("removed words");
        removedWord.getStyleClass().add("word-removed");
        Text addedWord = new Text("added words");
        addedWord.getStyleClass().add("word-added");
        TextFlow wordFlow = new TextFlow(removedWord, new Text("  ·  "), addedWord);
        wordFlow.setPadding(new Insets(2, 0, 0, 0));
        strip.getChildren().add(wordFlow);

        Label note = new Label("“formatting:” = only the look changed, not the words");
        note.getStyleClass().add("legend-note");
        strip.getChildren().add(note);

        return strip;
    }

    /** The fuller help, opened from the “How to read the diff” button. */
    public static void showHelpDialog(Window owner) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(20, 24, 12, 24));
        content.setPrefWidth(560);

        content.getChildren().addAll(
                heading("What you are looking at"),
                body("Docudiff compares two versions of the same document and shows you the "
                        + "difference. Nothing here is editable — this is a picture of what "
                        + "changed, not an editor."),
                heading("The coloured cards"),
                cardEntry("diff-modified", "Changed",
                        "The paragraph exists in both versions, but the wording or the formatting "
                                + "differs. The card is warm yellow."),
                cardEntry("diff-added", "New",
                        "The paragraph exists only in the newer version. The card is green."),
                cardEntry("diff-removed", "Deleted",
                        "The paragraph existed only in the older version. The card is red."),
                heading("Red and green words"),
                wordEntry("word-removed", "Struck through and red",
                        "these words were taken out"),
                wordEntry("word-added", "Bold and green",
                        "these words were put in"),
                heading("The “formatting:” lines"),
                body("Sometimes a paragraph's words do not change but its look does — someone "
                        + "made it bold, changed the font, or changed the size. Docudiff reports "
                        + "those underneath the paragraph, because a formatting change is still a "
                        + "change worth knowing about."),
                heading("Spreadsheets read differently"),
                body("Rows are matched by content, not by position. If someone inserts one row at "
                        + "the top of a long sheet, you see one inserted row — not every row below "
                        + "it marked as changed. A cell holding a formula is compared by the "
                        + "formula itself, so rewriting =SUM(A1:A5) as =A1+A2+A3+A4+A5 counts as a "
                        + "change even when the number stays the same."),
                heading("Markdown"),
                body("Compared line by line, like a programmer's diff, with changed words marked "
                        + "the same way."));

        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("How to read the diff");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().addAll(
                DiffLegend.class.getResource("/docudiff.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("help-dialog");
        dialog.showAndWait();
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("help-heading");
        return label;
    }

    private static Label body(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("help-body");
        return label;
    }

    private static HBox swatchEntry(String styleClass, String text) {
        Label swatch = new Label();
        swatch.getStyleClass().addAll("legend-swatch", styleClass);
        Label label = new Label(text);
        label.getStyleClass().add("legend-note");
        HBox entry = new HBox(6, swatch, label);
        entry.setAlignment(Pos.CENTER_LEFT);
        return entry;
    }

    private static HBox cardEntry(String styleClass, String title, String description) {
        Label swatch = new Label();
        swatch.getStyleClass().addAll("legend-swatch", styleClass);
        swatch.setPrefSize(26, 22);
        TextFlow flow = new TextFlow();
        Text strong = new Text(title + " — ");
        strong.getStyleClass().add("help-card-title");
        Text rest = new Text(description);
        rest.getStyleClass().add("help-body");
        flow.getChildren().addAll(strong, rest);
        HBox entry = new HBox(10, swatch, flow);
        entry.setAlignment(Pos.TOP_LEFT);
        return entry;
    }

    private static HBox wordEntry(String styleClass, String label, String example) {
        Text sample = new Text(example);
        sample.getStyleClass().add(styleClass);
        Label explanation = new Label("  —  " + label);
        explanation.getStyleClass().add("help-body");
        HBox entry = new HBox(4, sample, explanation);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(2, 0, 2, 0));
        return entry;
    }

    /** A button that opens the help dialog; used by the diff toolbar. */
    public static Button helpButton(Window owner) {
        Button button = new Button("How to read the diff");
        button.getStyleClass().add("help-button");
        button.setOnAction(event -> showHelpDialog(owner));
        return button;
    }
}
