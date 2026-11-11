package dev.docudiff.ui;

import javafx.application.Application;

/** Entry point for {@link CrashProbe}; must not extend {@link Application}. */
public final class CrashProbeLauncher {

    public static void main(String[] args) {
        Application.launch(CrashProbe.class, args);
    }
}
