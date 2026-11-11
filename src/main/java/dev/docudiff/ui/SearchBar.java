package dev.docudiff.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The search bar above the diff: query field, match counter, previous/next, and the note that
 * tells you when the current hit is not itself a change.
 *
 * <p>Reports state upward through {@link Listener} rather than driving the diff itself, so the
 * pane stays the only thing that knows how to render either a block diff or a cell grid.
 */
public final class SearchBar extends VBox {

    /** What the bar tells the diff pane when the query or the current match changes. */
    public interface Listener {
        void queryChanged(String query);

        void currentMatchChanged(int index);
    }

    private final TextField queryField = new TextField();
    private final Label counterLabel = new Label();
    private final Label contextNote = new Label();
    private final Button previousButton = new Button("‹");
    private final Button nextButton = new Button("›");
    private final Button clearButton = new Button("Clear");

    private Listener listener = new Listener() {
        @Override
        public void queryChanged(String query) {
            // replaced as soon as the pane attaches
        }

        @Override
        public void currentMatchChanged(int index) {
            // replaced as soon as the pane attaches
        }
    };

    private int matchCount;
    private int currentIndex = -1;

    public SearchBar() {
        setSpacing(3);
        setPadding(new Insets(6, 10, 6, 10));
        getStyleClass().add("search-bar");

        queryField.setPromptText("Find in this diff…");
        queryField.getStyleClass().add("search-field");
        queryField.setPrefWidth(260);
        queryField.textProperty().addListener((observable, oldValue, newValue) -> {
            currentIndex = -1;
            listener.queryChanged(newValue);
        });
        queryField.setOnAction(event -> next());

        previousButton.getStyleClass().add("search-step");
        previousButton.setOnAction(event -> previous());
        nextButton.getStyleClass().add("search-step");
        nextButton.setOnAction(event -> next());
        clearButton.getStyleClass().add("search-clear");
        clearButton.setOnAction(event -> clear());

        counterLabel.getStyleClass().add("search-counter");
        contextNote.getStyleClass().add("search-note");
        contextNote.setVisible(false);
        contextNote.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(6, queryField, counterLabel, previousButton, nextButton,
                spacer, clearButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(controls, contextNote);
        updateCounter();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** The current query, trimmed; empty when nothing is being searched. */
    public String query() {
        return queryField.getText() == null ? "" : queryField.getText().trim();
    }

    public boolean isActive() {
        return !query().isEmpty();
    }

    /**
     * Called by the diff pane once it knows how many matches the current query has.
     *
     * <p>Deliberately does not fire {@link Listener#currentMatchChanged}: the caller is already
     * inside a render, and calling back into it would recurse. The caller re-renders with
     * {@link #currentIndex()} if this clamped the index.
     */
    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
        if (matchCount > 0 && currentIndex < 0) {
            currentIndex = 0;
        }
        if (matchCount == 0) {
            currentIndex = -1;
        }
        updateCounter();
    }

    public int currentIndex() {
        return currentIndex;
    }

    /**
     * Explains that the hit is in unchanged content. The note lives here rather than inline in the
     * diff, so an unchanged block does not grow a line of text of its own.
     */
    public void setContextNote(String note) {
        boolean shouldShow = note != null && !note.isBlank();
        contextNote.setText(shouldShow ? note : "");
        contextNote.setVisible(shouldShow);
        contextNote.setManaged(shouldShow);
    }

    private void next() {
        step(1);
    }

    private void previous() {
        step(-1);
    }

    private void step(int direction) {
        if (matchCount == 0) {
            return;
        }
        int nextIndex = currentIndex + direction;
        if (nextIndex < 0) {
            nextIndex = matchCount - 1;
        }
        if (nextIndex >= matchCount) {
            nextIndex = 0;
        }
        currentIndex = nextIndex;
        updateCounter();
        listener.currentMatchChanged(currentIndex);
    }

    private void clear() {
        queryField.clear();
        currentIndex = -1;
        matchCount = 0;
        setContextNote(null);
        updateCounter();
    }

    /** Resets the bar to empty without firing the query listener's downstream work twice. */
    public void clearSearch() {
        clear();
    }

    private void updateCounter() {
        if (!isActive()) {
            counterLabel.setText("");
        } else if (matchCount == 0) {
            counterLabel.setText("no matches");
        } else {
            counterLabel.setText((currentIndex + 1) + " of " + matchCount);
        }
        boolean hasMatches = matchCount > 0;
        previousButton.setDisable(!hasMatches);
        nextButton.setDisable(!hasMatches);
        clearButton.setDisable(!isActive());
    }
}
