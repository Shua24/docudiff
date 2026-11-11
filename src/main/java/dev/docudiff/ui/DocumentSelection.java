package dev.docudiff.ui;

import dev.docudiff.library.DocumentFolder;
import dev.docudiff.versioning.VersionRepository;

/**
 * What an action class needs from the currently open document.
 *
 * <p>Both members are null when the library is open but no document is selected. Actions check
 * that before doing anything, which is why every action is safe to invoke from a toolbar that is
 * always visible.
 */
interface DocumentSelection {

    DocumentFolder document();

    VersionRepository repository();
}
