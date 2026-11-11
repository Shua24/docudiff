package dev.docudiff.ui;

import javafx.application.Application;

/** Entry point for {@link OverlayCapture}; must not extend {@link Application}. */
public final class OverlayLauncher {

    public static void main(String[] args) {
        Application.launch(OverlayCapture.class, args);
    }
}
