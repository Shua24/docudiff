package dev.docudiff.ui;

import dev.docudiff.compare.Comparison;
import dev.docudiff.compare.ComparisonSlot;
import dev.docudiff.compare.DocumentComparer;
import dev.docudiff.compare.DocumentSource;
import dev.docudiff.compare.DocumentSourceResolver;
import dev.docudiff.library.DocumentFolder;
import dev.docudiff.versioning.VersionEntry;
import dev.docudiff.versioning.VersionRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Diff tab: a header naming both sides, two slots, a search bar, the rendered diff, and the
 * legend strip.
 *
 * <p>This is also where a review happens. The header states which two things are being compared,
 * and "Export review report…" turns the comparison on screen into a Word document a reviewer can
 * send back. The comparison engine is the same code either way — a review is a diff with the
 * intent to report, not a separate mode.
 */
public final class DiffTab extends VBox {

    private final ComparisonBar comparisonBar = new ComparisonBar();
    private final SearchBar searchBar = new SearchBar();
    private final DiffPane diffPane = new DiffPane();
    private final StackPane legendHolder = new StackPane();
    private final Label sidesLabel = new Label("No comparison yet.");
    private final Button exportButton = new Button("Export review report…");
    private final DocumentComparer comparer = new DocumentComparer();

    private final BackgroundRunner runner;
    private final Consumer<String> statusSetter;
    private final ReportActions reportActions;

    private DocumentFolder documentFolder;
    private VersionRepository repository;
    private Comparison lastComparison;
    private int renderedMatchIndex = -1;

    public DiffTab(BackgroundRunner runner, Consumer<String> statusSetter) {
        super(0);
        this.runner = runner;
        this.statusSetter = statusSetter;
        this.reportActions = new ReportActions(runner, statusSetter);

        comparisonBar.setListener(this::compare);
        searchBar.setListener(new SearchBar.Listener() {
            @Override
            public void queryChanged(String query) {
                refreshSearch();
            }

            @Override
            public void currentMatchChanged(int index) {
                refreshSearch();
            }
        });

        sidesLabel.getStyleClass().add("comparison-sides");
        exportButton.setOnAction(event -> reportActions.export(
                lastComparison, getScene() == null ? null : getScene().getWindow()));
        exportButton.setTooltip(new javafx.scene.control.Tooltip(
                "Write the comparison on screen into a Word file you can send back"));
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(8, sidesLabel, headerSpacer, exportButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(6, 10, 0, 10));

        legendHolder.getChildren().setAll(DiffLegend.strip());
        VBox.setVgrow(diffPane, Priority.ALWAYS);
        getChildren().addAll(header, comparisonBar, searchBar, diffPane, legendHolder);

        // Start in the two-file case: with no document open, both slots offer a file chooser.
        comparisonBar.resetForNoDocument();
        diffPane.showPlaceholder(
                "Pick a document on each side to compare, or import a document "
                        + "and compare its versions.");
    }

    /** Points the tab at an open document and compares its two newest versions. */
    public void setDocument(
            DocumentFolder documentFolder,
            VersionRepository repository,
            List<VersionEntry> versions) {
        this.documentFolder = documentFolder;
        this.repository = repository;
        try {
            comparisonBar.setFilterFormat(documentFolder.format());
        } catch (Exception exception) {
            comparisonBar.setFilterFormat(null);
        }
        comparisonBar.setVersions(versions);
        compare();
    }

    /** Clears the tab back to the two-file case, with no document context. */
    public void clearDocument() {
        this.documentFolder = null;
        this.repository = null;
        this.lastComparison = null;
        sidesLabel.setText("No comparison yet.");
        comparisonBar.setFilterFormat(null);
        comparisonBar.resetForNoDocument();
        searchBar.clearSearch();
        diffPane.showPlaceholder("Pick a document on each side to compare, "
                + "or import a document and compare its versions.");
        statusSetter.accept("Ready.");
    }

    /** Runs the comparison for the two slots currently chosen. */
    private void compare() {
        Optional<ComparisonSlot> leftSlot = comparisonBar.leftSlot();
        Optional<ComparisonSlot> rightSlot = comparisonBar.rightSlot();
        if (leftSlot.isEmpty() || rightSlot.isEmpty()) {
            lastComparison = null;
            sidesLabel.setText("No comparison yet.");
            diffPane.showPlaceholder("Pick a document on each side to compare.");
            return;
        }
        DocumentSourceResolver resolver =
                new DocumentSourceResolver(repository, documentFolder);
        ComparisonSlot left = leftSlot.get();
        ComparisonSlot right = rightSlot.get();

        runner.run("Comparing documents…", () -> {
            DocumentSource leftSource = resolver.resolve(left);
            DocumentSource rightSource = resolver.resolve(right);
            return comparer.compare(leftSource, rightSource);
        }, this::showComparison);
    }

    private void showComparison(Comparison comparison) {
        this.lastComparison = comparison;
        sidesLabel.setText("Before: " + comparison.leftLabel()
                + "   ·   After: " + comparison.rightLabel());
        legendHolder.getChildren().setAll(
                comparison.isSpreadsheet() ? DiffLegend.stripForSheet() : DiffLegend.strip());
        diffPane.showComparison(comparison);
        searchBar.clearSearch();
        renderedMatchIndex = -1;
        String unit = comparison.isSpreadsheet() ? " changed row(s)." : " changed block(s).";
        statusSetter.accept(comparison.changedCount() + unit);
    }

    /**
     * Re-renders the diff for the current query and match index, then updates the counter and the
     * "this part is not changed" note.
     */
    private void refreshSearch() {
        String query = searchBar.query();
        int matchCount = diffPane.applySearch(query, searchBar.currentIndex());
        searchBar.setMatchCount(matchCount);

        // setMatchCount may have clamped the index from -1 to 0; re-render so that match is
        // emphasised rather than leaving the first hit unhighlighted.
        if (searchBar.currentIndex() != renderedMatchIndex
                && searchBar.currentIndex() >= 0) {
            diffPane.applySearch(query, searchBar.currentIndex());
        }
        renderedMatchIndex = searchBar.currentIndex();
        searchBar.setContextNote(diffPane.contextNoteFor(searchBar.currentIndex()));
    }
}
