package dev.docudiff.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * The app's small settings file, at {@code ~/.config/docudiff/settings.properties}.
 *
 * <p>Only one setting exists: where LibreOffice lives. The environment variable
 * {@code DOCUDIFF_LIBREOFFICE} still works, but the setting wins when both are present, because a
 * value the user typed into the app's own dialog must not be silently overridden by something in
 * their shell profile. See {@link dev.docudiff.render.OfficeLocator} for the full lookup order.
 *
 * <p>Reading never fails loudly: a missing or unreadable settings file means "no settings", not a
 * crashed start-up. Writing reports failure, because a setting the user typed and cannot save is
 * worse than an error message.
 */
public final class AppSettings {

    private static final String LIBREOFFICE_PATH_KEY = "libreoffice.path";

    private final Path settingsFile;
    private final Properties properties;

    private AppSettings(Path settingsFile, Properties properties) {
        this.settingsFile = settingsFile;
        this.properties = properties;
    }

    /** The real settings file for this user. */
    public static AppSettings load() {
        return at(defaultSettingsFile());
    }

    /** Reads settings from a specific file, which is how tests avoid the user's home directory. */
    public static AppSettings at(Path settingsFile) {
        Properties properties = new Properties();
        if (Files.exists(settingsFile)) {
            try (InputStream input = Files.newInputStream(settingsFile)) {
                properties.load(input);
            } catch (IOException exception) {
                // An unreadable settings file is not worth failing the app over: the lookup
                // falls through to the environment variable and PATH detection instead.
                properties.clear();
            }
        }
        return new AppSettings(settingsFile, properties);
    }

    public static Path defaultSettingsFile() {
        String override = System.getProperty("docudiff.settingsFile");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".config", "docudiff",
                "settings.properties");
    }

    public Path settingsFile() {
        return settingsFile;
    }

    /** The configured LibreOffice executable, or empty when the user has not set one. */
    public Optional<Path> libreOfficePath() {
        String value = properties.getProperty(LIBREOFFICE_PATH_KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(value.trim()));
    }

    /**
     * Stores the LibreOffice path. A blank value removes the setting, which is how the user says
     * "go back to detecting it automatically".
     */
    public void setLibreOfficePath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            properties.remove(LIBREOFFICE_PATH_KEY);
        } else {
            properties.setProperty(LIBREOFFICE_PATH_KEY, path.trim());
        }
        Files.createDirectories(settingsFile.getParent());
        try (OutputStream output = Files.newOutputStream(settingsFile)) {
            properties.store(output, "Docudiff settings");
        }
    }
}
