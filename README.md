# Docudiff

A desktop application that keeps **versions** of your Word, Excel, and Markdown documents, shows
you **what changed** between any two of them, and lets you **branch and merge** your documents the
way programmers branch and merge code — without ever touching a command line.

It is read-only by design: Docudiff never edits your document. You keep writing in Word, Excel, or
your Markdown editor; Docudiff records, compares, and preserves.

**Status:** MVP, working. 69 automated tests pass (`mvn test`). The application launches and the
full library / document / diff / versions window renders. Three features that were planned are
**not** implemented and are listed under [Not implemented yet](#not-implemented-yet) — read that
section before you rely on anything.

---

## 1. What Users Can Do

Docudiff is a small program that sits on your machine. There is no server, no account, no internet
connection, and nothing is uploaded anywhere.

### The idea in one paragraph

People who work with documents end up with `report-final.docx`, `report-final-v2.docx`, and
`report-final-ACTUALLY-final.docx`, and no reliable way to answer "what did I change on Tuesday,
and can I get that sentence back?" Docudiff answers both questions. It stores every version of a
document, shows you exactly what changed between any two documents (down to the word, and down to
a bold or font change) — including one you sent out and got back — and lets you keep parallel lines
of work that you can later bring back together.

### Getting your documents in: importing

Click **Import…** and pick a `.docx`, `.xlsx`, or `.md` file.

Docudiff copies the file into its own library at `~/Documents/Docudiff/<document-name>/` and
immediately records that copy as the first version, named `<name>-snapshot-<today's date>`.

- The original file you picked is **not** moved or modified. It stays exactly where it was.
- If you import a second file with the same name, Docudiff does not overwrite the first — the new
  one goes into `<name>-2/`.
- Only `.docx`, `.xlsx`, `.md`, and `.markdown` are accepted. Anything else is refused with a
  message.

### Where your documents live

```text
~/Documents/Docudiff/
  quarterly-report/                 ← one folder per document
    quarterly-report.docx           ← the working file (the one you edit)
    .git/                           ← the version history
    .docudiff/cache/                ← page-render cache (safe to delete)
```

The folder is deliberately **visible and ordinary**. You can back it up with whatever you already
use, copy it to another machine, or inspect it. Everything about a document lives inside its own
folder, so moving that folder moves the complete history.

### Making a version: snapshotting

1. Click **Open in LibreOffice**. The working file opens in LibreOffice (or Word, if that is your
   default for the file type).
2. Edit and **save**, then come back to Docudiff.
3. Click **Snapshot version**.
4. Docudiff suggests a name — `<document-name>-snapshot-<yyyy-mm-dd>` — and you can accept it or
   type your own. Press OK.
5. A dimmed panel with a spinner appears: *Making snapshot "…"*. It disappears on its own when the
   version is saved and the page view has been re-rendered.

**Why there is a busy panel at all.** Saving a version of a large document takes a moment — the
file is committed, then re-rendered for the page view. The overlay exists so that moment is
visibly *work in progress* rather than a window that seems to have ignored your click.

A second snapshot on the same day automatically becomes `...-2`, then `...-3`, so no two versions
ever share a name.

The status line at the bottom left tells you the state of the working file:

| Status | Meaning |
|---|---|
| `clean` | The file on disk is identical to the latest version. |
| `modified` | You (or something) changed the file since the last version. Snapshot it, or it will be lost when you switch branches. |
| `missing` | The working file was deleted or moved out of the folder. |

**Nothing is versioned until you press Snapshot version.** Saving in LibreOffice does not create a
version — that is deliberate, so you decide which states are worth keeping.

### Looking at a document: the Document tab

Pick a document in the left list, then pick a version in the **Versions** tab. The **Document**
tab shows that version the way it looked when it was saved:

- **Word documents** are shown as page images, rendered by LibreOffice. Browsing is paginated, so
  the text is exactly where the printer would put it.
- **Markdown documents** are shown as rendered, formatted text — headings, lists, code blocks and
  all.
- **Spreadsheets** are not shown as page images, because a spreadsheet's printed layout is not its
  working layout. They appear in the Diff tab as aligned rows instead.

If LibreOffice is not installed, the page view says so and **everything else keeps working**.
Install LibreOffice, or set the `DOCUDIFF_LIBREOFFICE` environment variable to its full path, and
restart Docudiff.

### Comparing documents: the Diff tab

This is the point of the program.

At the top of the tab are two slots, **From** and **To**. Each one can hold a version of the open
document, the working file as it is right now, or any file on disk. Pick one on each side and the
diff appears. The ⇄ button swaps them.

A version is what you get from **Snapshot version**; the working file is the document as it stands
in the library folder, including edits you made in LibreOffice and have not snapshotted yet.

1. To compare two versions, choose one in **From** and another in **To**. Opening a document
   defaults to its two newest snapshots, so usually there is nothing to pick.
2. To compare against the working file, choose **Working file (as it is now)** on either side.
   This answers "what have I changed since I last saved a version?" — a comparison that was not
   possible before, when only snapshotted versions could be compared.
3. To compare against a document someone else sent you, choose **Choose a file…** and pick it.
4. Changes are colour-coded: a warm card means edited, green means added, red means removed.

**Comparing a document that came back to you.** Send a document to a reviewer, get it back, and you
do not have to import it, branch anything, or give it a parent version: put a version of your
document on one side and their returned file on the other. The comparison is a pure document-to-
document diff and is never saved — it is a view, not a record. Two consequences worth knowing:

- The two sides must be the **same format**. Comparing a `.docx` against a `.xlsx` is refused with a
  message naming both formats, because the blocks do not correspond and there is nothing honest to
  show. The file picker is already filtered to the format of the other side, so the impossible pair
  is hard to reach by accident.
- A comparison is **read once, when you ask for it**. Bytes are captured at that moment, so editing
  the file afterwards does not silently change the diff you are looking at.

**Searching inside a diff.** The search box above the diff finds text in what is on screen. Matches
are highlighted in place; **‹** and **›** step between them and the counter says which one you are
on. Matching is case-insensitive and is a plain substring — no regex, no options.

When the current match is in a part of the document that did not change, the bar says so:

```text
1 of 3 · this part is not changed — the match is in an unchanged part of the document
```

That note matters: finding a word is not the same as finding a *change*, and a diff is exactly the
place where those two get confused.

While a search is active, a **spreadsheet diff renders every row**, not only the changed ones — the
summary switches to `showing all 5 row(s) · search active`. Without this, searching for a value in an
untouched row would report "no matches" for something visibly present in the sheet. Clearing the box
returns the grid to changed-rows-only.

**Reading the result.** A legend strip sits permanently under the diff, spelling out what each
colour and mark means, so you never have to remember it or go looking. If you want the longer
explanation — what a diff *is*, why formatting gets its own note, why spreadsheets behave
differently — click **How to read the diff** at the bottom right. Both are always available; the
strip is the reminder, the button is the reference.

What you get depends on the file type.

**Word documents.** Paragraphs are paired by similarity, so a reworded sentence shows as **one
modified paragraph** with the changed words marked — not as a deleted paragraph plus an inserted
one. Inside a changed paragraph, deleted words are struck through and coloured red; added words are
bold and green.

Formatting changes are reported too, as a small note under the paragraph:

```text
formatting: bold applied
formatting: font changed to Liberation Sans
formatting: size changed to 14
```

This means a change that alters no text at all — making a heading bold, for example — still shows
up as a change, because it *is* one.

Only the document **body and its headers and footers** are compared. Footnotes, endnotes, and
comments are not.

**Spreadsheets.** Rows are matched by **content**, not by position. This is the difference between
a useful diff and a useless one:

- Insert one row at the top of a 500-row sheet and Docudiff shows **one inserted row**.
- A program that compared row 1 to row 1 would report **501 changed rows**.

The diff is shown as a **cell grid**, not as a list of row numbers: column letters across the top,
row numbers down the left, and only the changed rows listed. A changed cell shows its old value
struck through in red and its new value in green, side by side — `10 → 12` — because a bare `12`
does not tell you what it used to be. A new row is green, a deleted row is red, and the legend
under the grid describes cells rather than paragraphs.

**Every sheet is compared, not just the first.** Sheets are matched by **name**, so inserting a new
tab does not make every later tab look rewritten. Only sheets that actually differ are shown; a
workbook with ten tabs and one change shows one grid, not ten. A sheet that exists on only one side
is reported as a whole sheet added or removed.

**Cell formatting is compared too.** Bolding a cell or changing its fill is a real change even when
no text moves, and it is reported underneath the row just as document formatting is reported under
a paragraph:

```text
formatting: A bold applied
formatting: B fill changed to FFFF00
```

A row whose only difference is presentation still counts as changed, so it is never silently
skipped.

A cell holding a formula is compared by its **formula text**, so rewriting `=SUM(A1:A5)` as
`=A1+A2+A3+A4+A5` is reported as a change even when both produce the same number. The displayed
value is shown as context.

Number formatting is preserved: a cell formatted as currency or to two decimals renders as the
sheet shows it (`2.50`, not `2.5`), so money columns do not read as spurious changes.

**Markdown.** Compared line by line, like a programmer's diff, with changed words marked.

You can compare **any two documents**, including versions on different branches — pick one on each
side, and the diff shows the difference between them regardless of where they sit in the history,
or whether the right-hand side is in the library at all.

### Branching

A branch is a parallel line of versions. Use one when you want to try a different direction
without disturbing the version you are happy with.

1. Click **New branch…** and give it a name (the suggested name is `experiment`).
2. Click **Switch branch…** and choose it. The working file is replaced with that branch's
   content.
3. Edit and snapshot as usual. Those versions go on the new branch.
4. Switch back to `main` whenever you like — the working file returns to `main`'s content.

**Switching is refused while the working file is modified.** Docudiff will not silently discard an
unsaved edit; it tells you to snapshot or discard the change first.

### Merging

Click **Merge branch…** and choose which branch to take content from. That branch's version
becomes the current branch's new content, and the history records that the two lines joined.

**This is not a content merge.** Docudiff does not attempt to combine changes from both branches
word by word — that is genuinely hard to do correctly for a Word document and would produce
corrupted files when it went wrong. Merging means: *"I have decided this branch's version wins."*
If both branches changed the same paragraph, one version is chosen, not blended.

### Things that are safe to do

- Delete the `.docudiff/cache/` folder inside a document — it only holds regenerated page images.
- Move or rename a document's **whole folder** while Docudiff is closed.
- Open the same library from a backup or a copied machine — a document folder is self-contained.

### Things that will cause trouble

- Renaming the document file *inside* the folder by hand. Docudiff looks for the file by name.
- Editing the working file while Docudiff is comparing large documents — you may snapshot a
  half-saved file.
- Two Docudiff windows pointed at the same library at the same time. There is no locking.

---

## 2. Developer Onboarding

### Prerequisites

| Requirement | Verified version on the development machine | Notes |
|---|---|---|
| JDK | OpenJDK 25.0.5 | Java 25 is the compile target (`maven.compiler.release=25`). |
| Maven | Apache Maven 3.10.0 | Builds and runs everything. |
| LibreOffice | LibreOffice 26.8.1.1 | Optional at runtime; required for the Word page view and for `DocxPdfRendererTest`. |

Verified with `java -version`, `mvn -version`, and `soffice --version`.

JavaFX is **not** installed on the system and must not be. It comes from Maven Central
(`org.openjfx:javafx-controls:27.0.1`), which is compiled for JDK 25. Do not substitute a distro
`openjfx` package — the current one is 17.x and will not run against Java 25.

### Quick start

```bash
mvn -q -B javafx:run     # build and launch the application
mvn -q -B test           # run all 69 tests
mvn -q -B compile        # compile only, no tests
```

`javafx:run` keeps the process alive while the window is open. It prints nothing on a successful
launch.

### Running the tests

```text
Tests run: 69, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Three of those tests shell out to LibreOffice and take about fifteen seconds
(`Tests run: 3 ... Time elapsed: 15.09 s`). When LibreOffice is absent they **skip** rather than
fail:

```java
assumeTrue(OfficeLocator.locate().isPresent(),
        "LibreOffice is not installed; the renderer tests are skipped on this machine");
```

A skipped `DocxPdfRendererTest` is therefore expected on a machine without LibreOffice. Every other
test runs everywhere.

### Repository layout

```text
pom.xml
src/main/java/dev/docudiff/
  Launcher.java              process entry point — see the invariant below
  DocudiffApplication.java   builds the window and applies docudiff.css
  library/                   the on-disk library: root, document folders, import
  versioning/                JGit: snapshots, branches, fork merge, working-file state
  document/                  the shared document model (blocks, spans, sheets)
  extract/                   one extractor per format: docx, xlsx, markdown
  diff/                      alignment, word marks, formatting annotations, row alignment
  render/                    LibreOffice invocation and PDF page rendering
  ui/                        JavaFX panes, dialogs, and the comparison wiring
src/main/resources/docudiff.css
src/test/java/dev/docudiff/  mirrors the packages above, plus EndToEndTest
```

Nothing under `target/` is hand-written; it is all generated by Maven and is never edited.

Largest source file is 256 lines (`versioning/VersionRepository.java`); every file in `src/` is
under 300.

### Architecture: the dependency direction

```text
ui  ──►  diff  ──►  document  ◄──  extract
 │                                 ▲
 └──►  library  ──►  versioning  ───┘
```

The rule that keeps this maintainable: **`diff` knows nothing about docx, xlsx or markdown.** Every
format is reduced to a `DocumentTree` (an ordered list of `Block`s) or a `WorkbookModel` before any
comparison happens. Adding a new format means writing one extractor and one renderer — the diff
engine does not change.

`versioning` knows nothing about documents at all. It stores bytes and commits; a document is just a
file in a repository.

### The comparison pipeline

```text
version blob ─┐
file on disk ─┼─► DocumentSourceResolver ─► DocumentSource ─┐
working file ─┘   (label, bytes, format)                    │
                                                            ▼
                                                    DocumentComparer
                                                            │
                                    ┌───────────────────────┴───────────────────────┐
                                    ▼                                               ▼
                              extractor ─► DocumentTree ─► DocumentDiffer      XlsxExtractor
                                    │            │                              │
                            ParagraphAligner  WordDiffer + FormattingAnnotator  SheetRowAligner
                                    │                                               │
                                    └──────────────► Comparison ◄───────────────────┘
```

`DocumentComparer` takes two `DocumentSource`s and nothing else. It has no repository, no version
and no working file, which is why version-against-version, version-against-returned-file,
file-against-file and working-file-against-version are all the same code path. Callers fetch bytes;
the comparer decides what changed.

`DocumentDiffer` diffs **section by section**, matching sections by name (`Body`, `Header 1`,
`Footer 1`). This is what stops a header paragraph from pairing with a body paragraph holding
identical text.

### Invariants — these will bite you if changed

**1. The main class must not extend `Application`.** `Launcher` holds `main`. Putting `main` back
into `DocudiffApplication` reproduces:

```text
Error: JavaFX runtime components are missing, and are required to run this application
```

The JVM refuses to start an `Application` subclass when JavaFX is on the classpath rather than the
module path.

**2. Never call JGit's `MergeCommand` on a document repository.** Measured behaviour on a
conflicting document: it returns `MergeStatus.Conflicting` **without throwing**, writes

```text
<<<<<<< HEAD
```

conflict markers into the working file, and leaves the branch ref untouched. The user would find
their document corrupted after a merge that reported no error. `VersionRepository.mergeForkOnly`
builds the commit by hand with `CommitBuilder` for exactly this reason.

**3. Every LibreOffice conversion needs its own profile directory.** Pass
`-env:UserInstallation=file://<dir>`. Measured: three concurrent conversions sharing the default
profile produced **two** PDFs; the dropped job printed nothing and exited 0. With per-job profiles,
three of three. `DocxPdfRenderer` already does this and deletes the profile afterwards.

**4. `ParagraphAligner` needs both stages.** The equalizer predicate pairs similar paragraphs; the
gap-walk between deltas recovers blocks the equalizer reported as equal but whose text differs.
Removing either stage silently loses or invents changes. The similarity function is **bigram
Jaccard at 0.35**, chosen because it separates real edits from unrelated text:

| Pair | Score | Must pair? |
|---|---|---|
| one-word edit | 0.837 | yes |
| number-only change | 0.741 | yes |
| word deleted | 0.718 | yes |
| clause added | 0.426 | yes |
| replaced paragraph | 0.245 | no |
| unrelated text | 0.018 | no |

Word Jaccard was rejected: it scores "clause added" at 0.25, which would render one edited
paragraph as a deletion plus an insertion.

**5. A `CHANGE` delta is not a pair.** It means "these ranges differ". `appendChange` re-checks each
pair against the similarity threshold; without that, two unrelated paragraphs render as one
`MODIFIED` block.

**6. Count deltas and count changes separately.** A three-paragraph case yields **2 deltas** but
**3 changed blocks**. Tests assert the status *sequence*, never a total.

**7. `XWPFRun.isBold()` cannot express "inherited".** It returns a primitive `boolean` — `false`
both for "explicitly not bold" and for "states nothing". Only `getFontFamily()` and
`getFontSizeAsDouble()` return null when unstated. To distinguish, read the run properties XML:
an empty `getBList()` means unstated. **POI 6.0.0 uses list accessors** — the singular `getB()` /
`isSetB()` do not exist.

**8. Viewing a version must not touch the working file.** Use `bytesOfVersion` to look, `materialize`
only to check out. Checking out an *older* version deliberately leaves the file `MODIFIED` relative
to the head, so a following snapshot records an intentional revert instead of losing it.

**9. `git.log()` throws `NoHeadException` on a fresh repository.** `listVersions` catches it and
returns an empty list; removing that guard crashes the version list on any newly imported document.

**10. `RevCommit` is invalid once its `RevWalk` closes.** Reading a parent's `getFullMessage()`
afterwards throws `NullPointerException` inside `RawParseUtils.commitMessage`. Call
`walk.parseBody(parent)` inside the walk.

**11. Spreadsheet rows align by content, never by position.** Measured on 500 rows plus one inserted
at the top: content alignment reports **1** insertion; position-by-position reports **501** changed
rows.

**12. Never call `Platform.exit()` while a modal dialog is open.** It segfaults the JVM on Linux:

```text
SIGSEGV (0xb) at pc=0x00007f3384000278
C  [libglassgtk3.so+0x9de5c]  Java_com_sun_glass_ui_gtk_GtkWindow__1close+0xc
j  com.sun.glass.ui.Window.close()
j  com.sun.glass.ui.Application.terminate()
j  com.sun.javafx.tk.quantum.QuantumToolkit.lambda$exit$0()
```

The toolkit tears down the native window while the dialog's nested event loop is still running.
Every dialog here uses `showAndWait()`, so **closing the window with a dialog open reaches this**.
Reproduced deterministically. `SafeExit` fixes it: implicit exit is off, dialogs are cancelled
first, and `Platform.exit()` is deferred to a later pulse. Do not remove `SafeExit.install(stage)`
from `DocudiffApplication`, and do not switch a dialog to `show()` — `showAndWait()` is what makes
the ordering deterministic.

**13. Search highlighting is built during rendering, not applied afterwards.** The document renderer
emits one `Text` node per word mark, so a match can straddle a word boundary or sit inside one.
Walking the scene graph after the fact cannot split those nodes. `SearchHighlighter.appendHighlighted`
returns the number of matches it emitted, and `DiffPane` records a `MatchSpot` for each one in render
order — that ordering is what the ‹ › buttons navigate by, so it must stay in step with what is
actually on screen.

**14. A spreadsheet diff renders unchanged rows only while a search is active.** `SheetDiffView`
skips `UNCHANGED` rows normally, which would make a keyword in an untouched row unfindable and read
as a broken search. The summary line switches to `showing all N row(s) · search active` so the count
never lies about which mode it is in.

**15. A formatting-only row change must promote the status to `MODIFIED`.** `SheetRowAligner` aligns
by content and returns `UNCHANGED` for a row whose text is identical — but `SheetDiffView` and
`SheetDiffView.isChanged()` skip `UNCHANGED` rows, so a bold-only or fill-only change would be
dropped silently. `WorkbookDiffer.attachFormatting` attaches the annotations *and* promotes the
status. `RowDiff.isChanged()` is the single predicate the view must ask; do not go back to comparing
`status() != UNCHANGED`, which was the bug.

**16. Never read `sheets().get(0)`.** Comparing workbooks means matching every sheet by name via
`WorkbookDiffer`. The original comparer took the first sheet of each workbook and reported a
workbook with a change on tab two as identical — a confident wrong answer rather than a visible
gap.

### Adding a new document format

1. Write a `FooExtractor` that produces a `DocumentTree` (or a `WorkbookModel` for tabular formats).
2. Add the extension to `DocumentFormat`.
3. Teach `DocumentComparer` which extractor to use, and `DocumentViewPane` which renderer.
4. Add a test that extracts a real generated file and asserts block counts and text — follow
   `DocxExtractorTest`.

`DocumentDiffer`, `ParagraphAligner`, `WordDiffer`, and the whole versioning layer need no change.

### Conventions in this codebase

- PascalCase for types, camelCase for locals and parameters. No abbreviations in identifiers.
- Every method that returns a value assigns it to a named local first.
- No file exceeds 300 lines. `MainWindow` was split into `DocumentActions` (dialogs) and `DiffTab`
  (the comparison surface) to stay under it; keep that split.
- Comments explain *why*, and where a decision was measured, the measurement is quoted in the
  comment. The `mergeForkOnly` and `DocxPdfRenderer` comments are the model to follow.
- Code that can take seconds (LibreOffice, diffing large files) runs on a background thread via
  `runOffThread`; the JavaFX thread is never blocked.

### Continuous integration

`.github/workflows/ci.yml` runs on every push to `main` and `dev`, on pull requests, and on
demand.

**`test`** — a matrix over `ubuntu-latest` and `windows-latest`, JDK 25 (Temurin), Maven cache.
LibreOffice is installed on both, because the renderer tests skip themselves via `assumeTrue` when
it is missing, and a skipped test is a green build that proves nothing. On Linux the suite runs
under `xvfb-run`: JavaFX cannot start its toolkit without a display and fails with
`Unable to open DISPLAY`.

**`package`** — builds the jar, stages it with its runtime dependencies, and produces an app image
with `jpackage` on each OS. No cross-compilation: the Windows image is built on Windows.

**Verifying a package is the part that is easy to get wrong.** `jpackage` creates the output
directory even when it fails — a failed run on this machine left `target/dist/Docudiff` present
with **zero files** in it — so "the directory is not empty" proves nothing. The workflow checks for
the launcher binary itself (`bin/Docudiff` on Linux, `Docudiff.exe` on Windows) and fails the job
if it is absent.

### Known gaps in the build

- `jpackage --type app-image` **fails on this machine**:
  `jlink failed with: Error: This JDK does not support linking from the current run-time image`.
  The openSUSE JDK package ships no `jmods/`. Fix with
  `sudo zypper install java-25-openjdk-jmods` if an app image is needed locally. `javafx:run` is
  unaffected, and CI uses Temurin, which ships `jmods`.
- Measured on this JDK, that failure **exits 1**, not 0 — but the output directory is created
  anyway, so neither the exit status nor the directory's existence is a reliable signal. Check for
  the launcher binary.
- The Windows job has not been run yet. The workflow is written for it, but until it executes once
  on `windows-latest`, Windows support is unverified rather than working.

### Not implemented yet

The following were planned and are **absent** from the code. Do not document them as working:

- **Deleting a branch.** Branches can be created and switched; `VersionRepository` has no
  `deleteBranch`.
- **"Discard working changes".** The branch switch refuses to proceed when the file is modified,
  but there is no button to throw those changes away — snapshot or restore the file by hand.
- **A settings pane.** The LibreOffice path is set through the `DOCUDIFF_LIBREOFFICE` environment
  variable only.
- **Deleting a snapshot.** History is append-only by design.
- **Merging content.** Merge takes one branch's version wholesale; no three-way merge exists.
- **Footnotes, endnotes, comments, nested tables.** Not extracted, therefore not diffed.
- **Windows packaging.** The code is written portably but only a Linux build has been produced.
- **Google Docs.** A Google Doc must be exported to `.docx` first.

---

## License

BSD 3-Clause. See [LICENSE](LICENSE).

Chosen because every runtime dependency is compatible with it:

| Dependency | License |
|---|---|
| JGit | EDL v1.0 / BSD-3-Clause |
| Apache POI | Apache 2.0 |
| PDFBox | Apache 2.0 |
| log4j-core | Apache 2.0 |
| java-diff-utils | Apache 2.0 |
| commonmark-java | BSD-2-Clause |
| JavaFX | GPLv2 + Classpath Exception |

Two notes for anyone tempted to relicense. **GPLv2 is not an option here**: Apache 2.0 is
incompatible with GPLv2 — the FSF and the ASF both say so — and four dependencies above are
Apache 2.0, so the combination is undistributable. Apache 2.0 itself is a bad fit for the same
reason in reverse: the project could be Apache-licensed, but then it could not be combined with
GPLv2 code, and JavaFX's Classpath Exception is what keeps that door open. BSD-3 has no conflict
anywhere in the stack, which is why it was picked.
