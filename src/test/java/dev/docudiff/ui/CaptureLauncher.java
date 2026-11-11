package dev.docudiff.ui;

import javafx.application.Application;

/**
 * Entry point for {@link CaptureHarness}.
 *
 * <p>Same invariant as the real app: a class holding {@code main} must not extend
 * {@link Application}, or the JVM aborts with "JavaFX runtime components are missing".
 */
public final class CaptureLauncher {

    public static void main(String[] args) {
        Application.launch(CaptureHarness.class, args);
    }
}
