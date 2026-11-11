# Manual test script

Everything automated lives in `src/test/java`. This file is the part a test cannot cover: what the
window actually looks like, what happens when you click the wrong thing first, and whether the
wording of a dialog tells the truth.

Run through it in order. Each step names what you should see, not just "it works". If a step behaves
differently, that is a real finding — write down the step number and what appeared instead.

---

## Setup

Use a throwaway library, not your real documents. The commands build one with:

- `report` — three snapshots, with the **middle one already marked deleted**
- `annotated` — two snapshots of a document containing a **footnote**

```bash
cd ~/Sources/docudiff
mvn -q -B test-compile
mvn -q -B dependency:build-classpath -Dmdep.outputFile=target/cp.txt
CP="target/classes:target/test-classes:$(cat target/cp.txt)"

DEMO=/tmp/docudiff-demo
rm -rf "$DEMO" && mkdir -p "$DEMO"
java -Duser.home="$DEMO" -Ddemo.extras=true -cp "$CP" \
     dev.docudiff.ui.DemoLibraryBuilder "$DEMO/source/report.docx"

java -Duser.home="$DEMO" -cp "$CP" dev.docudiff.Launcher
```

Expected on launch: the library lists **annotated** and **report**. Nothing is selected yet, the
status line says `Ready.`, and the Diff tab shows the placeholder about picking a document on each
side.

To test against your own library instead, just run `mvn -q -B javafx:run`.

---

## 1. The toolbar and the More menu

1. Look at the bottom row of buttons.

   **Expected:** `Import…`, `Snapshot version`, `Open in LibreOffice`, `New branch…`,
   `Switch branch…`, `Merge branch…`, `More ▾`, then on the right `How to read the diff`.

2. Click **More ▾**.

   **Expected:** four entries, in this order — `Delete selected version…`,
   `Discard working changes…`, a separator, `Delete branch…`, `Settings…`.

   *A menu rather than seven more buttons is deliberate: the destructive actions should cost one
   extra click, and should not sit next to `Snapshot version`.*

## 2. Versions hide a deleted one until asked

1. Click **report** in the library, then open the **Versions** tab.

   **Expected:** two versions listed — `quarterly-report-snapshot-<today>` and
   `report-snapshot-<today>`. The unlabelled `Show deleted` checkbox at the top is **unticked**.

2. Tick **Show deleted**.

   **Expected:** a third row appears between them:
   `quarterly-report-snapshot-<yesterday>  (deleted)` with `held by a branch` and a **Restore**
   button on the right.

   *This particular version is not a branch head: it is visible only because you ticked the box.
   Step 4 covers the case where the deleted version is the head.*

3. Untick it again.

   **Expected:** the row disappears; the other two stay.

## 3. Delete and restore a version

1. With **Show deleted** ticked, select `quarterly-report-snapshot-<today>` (the newest).

2. Click **More ▾ → Delete selected version…**

   **Expected:** a confirmation naming that version, saying it disappears from the list, that
   nothing is rewritten, and that it comes back with "Show deleted". Cancel first: nothing changes.

3. Do it again and confirm.

   **Expected:** the row stays visible (it is the branch head), now labelled `(deleted)` with a
   **Restore** button. The status line says `Deleted version …`.

4. Press **Restore** on that row.

   **Expected:** the `(deleted)` label disappears immediately and the Restore button goes away. The
   status line says `Restored version …`.

5. Delete a version that is **not** a branch head (the oldest one), confirm, and leave
   **Show deleted** unticked.

   **Expected:** it vanishes from the list entirely — no head holds it, so nothing forces it to stay
   on screen.

6. Restore it via **Show deleted**.

   **Expected:** back to three versions, all normal.

## 4. The Diff tab pickers never offer a deleted version

1. Delete the oldest version again and keep the tab on **Diff**.

   **Expected:** the two slot dropdowns list `Working file (as it is now)`, the two live versions,
   and `Choose a file…`. The deleted one is **not** offered.

2. Compare the newest version against the working file.

   **Expected:** the header above the pickers reads
   `Before: <left label>   ·   After: <right label>`. The status line reports a number of changed
   blocks (or `0 changed block(s).`).

3. Press **Swap**.

   **Expected:** the two slot values swap and the header's Before/After labels swap with them — the
   header must describe what is actually on screen, not what was picked earlier.

## 5. Discard working changes

1. With `report` open, click **Open in LibreOffice**, change a paragraph, save, and close.

2. Back in Docudiff, look at the status line, then click **More ▾ → Discard working changes…**

   **Expected:** a confirmation that names the file and says edits since the last snapshot cannot be
   recovered. Cancel: nothing changes.

3. Confirm.

   **Expected:** the status line says the working file matches the newest snapshot again. Reopening
   the document in LibreOffice shows the **pre-edit** text.

4. With no changes pending, click it again.

   **Expected:** no dialog at all — the status line just says the file already matches.

5. In your file manager, delete the document file from the library folder, then click it again.

   **Expected:** the confirmation says "Put the missing file back?" and confirming recreates the
   file with the newest snapshot's content.

## 6. Delete a branch

1. **New branch…** → name `experiment` → snapshot something on it.
2. **Switch branch…** → `main`.
3. **More ▾ → Delete branch…**

   **Expected:** the chooser lists `experiment` and **not** `main`; cancelling changes nothing.

4. Choose `experiment`, confirm.

   **Expected:** the branch is gone from `Switch branch…`, and the status line says
   `Deleted branch experiment.`

5. Switch back to a state where `main` is the only branch, then click **Delete branch…** again.

   **Expected:** no chooser — the status line says
   `This document has only one branch, and it is the one you are on.`

   *Deleting the branch you are on is refused by git itself. The app pre-filters it so you never see
   a raw git error.*

## 7. The "not compared" notice

1. Open **annotated**, go to the Diff tab, and let it compare.

   **Expected:** above the diff, a pale yellow banner reading
   `"<label>" contains footnotes (1), which are not compared.`

   *This is the whole point of the feature: before it, a document whose only change was a footnote
   or a comment could read as identical.*

2. Open `report` instead.

   **Expected:** no banner — that document has no footnotes, comments, or nested tables.

## 8. Export a review report

1. With a document open and a real comparison on screen, click **Export review report…**

   **Expected:** a save dialog titled "Save review report", starting in `~/Documents`, with a
   suggested name like `review-<left>-vs-<right>.docx`.

2. Save it and open the file in LibreOffice Writer.

   **Expected:**
   - Title `Review report`, then `Before:  <label>`, `After:  <label>`, a `Generated …` line.
   - A bold summary line: `N changed block(s) between the two files.` (or `N changed row(s)` for a
     workbook).
   - Section headings (`Body`, `Header 1`, …) above the changed blocks.
   - Removed words **struck through** in dark red, inserted words **underlined** in blue.
   - `formatting: bold applied` and friends as small grey lines under the block they belong to.
   - A notice line for anything not compared, if the file had footnotes/comments.

3. Compare two **identical** versions and export again.

   **Expected:** the report says `No differences found.` and nothing else besides the header. The
   export button is **not** disabled.

4. Export a **spreadsheet** comparison (open the xlsx, change a cell, snapshot, diff).

   **Expected:** one heading per changed sheet (`Sheet "…"`), a grey context line per changed row,
   and a small **before/after table** with the changed cell shaded and bold. Two rows per change, one
   for the old values and one for the new.

5. Confirm nothing else was created: the document folder contains no new files, and the version list
   did not gain an entry.

## 9. Settings

1. With `report` open, click **More ▾ → Settings…**

   **Expected:** one field, showing the detected LibreOffice path (or empty), a **Browse…** button,
   and a grey line naming what is currently detected.

2. Type a path that does not exist (`/nope/soffice`), then Save.

   **Expected:** the status line says settings were saved and that no LibreOffice was found. Open the
   **Document** tab: the page view shows a labelled placeholder instead of crashing, and the **Diff**
   tab still works. Reopening Settings shows a warning that the saved path is not executable.

3. Click **Browse…**, pick the real `soffice`, and confirm the detection line now shows the version.

4. Save, then reopen the Document tab.

   **Expected:** the page image renders again.

5. Empty the field and save.

   **Expected:** detection falls back to the environment variable / `PATH`, and the dialog shows the
   detected path again.

## 10. Graceful degradation without LibreOffice

1. Quit the app.
2. `PATH=/usr/bin:/bin DOCUDIFF_LIBREOFFICE= mvn -q -B javafx:run` (or start the packaged app with
   LibreOffice uninstalled).

   **Expected:** the app starts, the Document tab explains that the page view needs LibreOffice, and
   the Diff tab still produces diffs and reports.

## 11. Things that look like bugs and are not

- **The diff is blank for a second after you pick a version.** The comparison runs off the UI thread
  so the window stays responsive; a spinner covers it.
- **A deleted version is still listed.** It is a branch head. It cannot be hidden without leaving a
  branch pointing at something invisible.
- **The version list shows the same date twice.** Snapshot names include the date and gain `-2`,
  `-3` on collision — check the suffix.
- **A snapshot you deleted still occupies its name.** Names are never reused; the next snapshot of
  that day becomes `-2`.
- **Merging refuses to blend text.** By design: merge takes one branch's version wholesale. Use the
  Diff tab and the review report to decide, instead.
- **The report is not in the library.** It is written where you saved it, and deliberately not
  inside the document's folder — otherwise the next snapshot would commit it as content.
