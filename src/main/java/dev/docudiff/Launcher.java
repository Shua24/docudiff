package dev.docudiff;

import javafx.application.Application;

/**
 * Process entry point.
 *
 * <p>This class must NOT extend {@link Application}. When JavaFX is on the classpath rather than
 * the module path, the JVM checks whether the main class is an {@code Application} subclass and
 * aborts with "Error: JavaFX runtime components are missing, and are required to run this
 * application". Keeping {@code main} in a plain class avoids that check entirely.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(DocudiffApplication.class, args);
    }
}
