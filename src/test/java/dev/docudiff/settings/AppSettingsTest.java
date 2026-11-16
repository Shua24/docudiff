package dev.docudiff.settings;

import dev.docudiff.render.OfficeLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings file, and the rule the user chose: a path typed into the settings dialog wins over
 * the {@code DOCUDIFF_LIBREOFFICE} environment variable, which stays as the fallback.
 */
class AppSettingsTest {

    @TempDir
    Path temporaryDirectory;

    private Path settingsFile;

    @BeforeEach
    void setUp() {
        settingsFile = temporaryDirectory.resolve("settings.properties");
        System.setProperty("docudiff.settingsFile", settingsFile.toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("docudiff.settingsFile");
    }

    @Test
    @DisplayName("a missing settings file means no setting, not a crash")
    void missingFileIsEmpty() {
        AppSettings settings = AppSettings.load();

        assertTrue(settings.libreOfficePath().isEmpty());
        assertFalse(Files.exists(settingsFile), "reading must not create the file");
    }

    @Test
    @DisplayName("a written path is read back")
    void pathIsStored() throws Exception {
        AppSettings.load().setLibreOfficePath("/opt/libreoffice/program/soffice");

        Optional<Path> readBack = AppSettings.load().libreOfficePath();

        assertEquals(Path.of("/opt/libreoffice/program/soffice"), readBack.orElseThrow());
    }

    @Test
    @DisplayName("a blank path removes the setting, which restores auto-detection")
    void blankPathClears() throws Exception {
        AppSettings.load().setLibreOfficePath("/opt/libreoffice/program/soffice");

        AppSettings.load().setLibreOfficePath("  ");

        assertTrue(AppSettings.load().libreOfficePath().isEmpty());
    }

    @Test
    @DisplayName("the configured path is what the locator returns")
    void configuredPathWins() throws Exception {
        Path fakeOffice = fakeExecutable();

        AppSettings.load().setLibreOfficePath(fakeOffice.toString());

        assertEquals(fakeOffice, OfficeLocator.locate().orElseThrow(),
                "a path the user typed in the settings dialog must not be overridden by "
                        + "automatic detection");
    }

    @Test
    @DisplayName("a configured path that is not executable is skipped, not trusted")
    void nonExecutablePathIsSkipped() throws Exception {
        Path missing = temporaryDirectory.resolve("not-here").resolve("soffice");

        AppSettings.load().setLibreOfficePath(missing.toString());

        Optional<Path> located = OfficeLocator.locate();
        assertTrue(located.isEmpty() || !located.get().equals(missing),
                "a path that cannot be executed must not be presented as LibreOffice");
    }

    private Path fakeExecutable() throws Exception {
        Path executable = temporaryDirectory.resolve("fake-soffice");
        Files.writeString(executable, "#!/bin/sh\necho 'LibreOffice 26.8.1.1'\n");
        assertTrue(executable.toFile().setExecutable(true), "could not mark the fixture executable");
        return executable;
    }
}
