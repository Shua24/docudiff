package dev.docudiff.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * A blocking overlay shown while a slow operation runs, so the window never looks frozen.
 *
 * <p>The scrim is deliberately semi-opaque rather than transparent: it stops the user clicking
 * buttons that would start a second conversion on top of the first, which is exactly what the
 * lag made them want to do.
 */
public final class BusyOverlay extends StackPane {

    private final Label messageLabel = new Label();
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final VBox card = new VBox(12);

    public BusyOverlay() {
        getStyleClass().add("busy-overlay");
        setAlignment(Pos.CENTER);
        setPickOnBounds(true);

        spinner.setPrefSize(42, 42);
        messageLabel.getStyleClass().add("busy-message");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(420);
        messageLabel.setAlignment(Pos.CENTER);

        card.getChildren().addAll(spinner, messageLabel);
        card.getStyleClass().add("busy-card");
        card.setPadding(new Insets(26, 34, 26, 34));
        card.setAlignment(Pos.CENTER);
        // StackPane stretches children up to their maximum size, and a VBox has none by default —
        // without this the card fills the entire window and the spinner floats in a sea of white.
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        getChildren().add(card);
        hideNow();
    }

    /** Shows the overlay and starts the spinner. */
    public void show(String message) {
        setMessage(message);
        setVisible(true);
        setManaged(true);
        setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(120), this);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    /** Changes the text without restarting the fade, for multi-stage operations. */
    public void setMessage(String message) {
        messageLabel.setText(message);
    }

    public void hide() {
        if (!isVisible()) {
            return;
        }
        FadeTransition fadeOut = new FadeTransition(Duration.millis(140), this);
        fadeOut.setFromValue(getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> hideNow());
        fadeOut.play();
    }

    private void hideNow() {
        setVisible(false);
        setManaged(false);
        setOpacity(0);
    }
}
