package dev.docudiff.report;

import dev.docudiff.document.Block;
import dev.docudiff.document.InlineSpan;
import dev.docudiff.document.SheetCell;
import dev.docudiff.document.SheetRow;

import java.util.List;

/**
 * Plain text for report writers.
 *
 * <p>Deliberately separate from every renderer: a report must show the document's own words, never
 * an internal alignment key. A spreadsheet row's {@code signature()} is escape-delimited for the
 * alignment algorithm; printing it would produce one unreadable line with no column boundaries,
 * which is how a previous spreadsheet view ended up showing coordinates instead of values.
 */
final class ReportText {

    private static final String COLUMN_SEPARATOR = "  |  ";

    private ReportText() {
    }

    static String textOf(Block block) {
        if (block == null) {
            return "";
        }
        if (block instanceof Block.Paragraph paragraph) {
            return joinSpans(paragraph.spans());
        }
        if (block instanceof Block.Heading heading) {
            return joinSpans(heading.spans());
        }
        if (block instanceof Block.CodeBlock codeBlock) {
            return String.join("\n", codeBlock.lines());
        }
        return "";
    }

    static List<InlineSpan> spansOf(Block block) {
        if (block instanceof Block.Paragraph paragraph) {
            return paragraph.spans();
        }
        if (block instanceof Block.Heading heading) {
            return heading.spans();
        }
        return List.of();
    }

    static boolean isHeading(Block block) {
        return block instanceof Block.Heading;
    }

    static boolean isCode(Block block) {
        return block instanceof Block.CodeBlock;
    }

    /** A cell's content as the user reads it: the formula, or the displayed value. */
    static String cellText(SheetCell cell) {
        if (cell == null) {
            return "";
        }
        return cell.contentKey();
    }

    /** A whole row as one readable line, columns separated. */
    static String rowText(SheetRow row) {
        if (row == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (SheetCell cell : row.cells()) {
            if (builder.length() > 0) {
                builder.append(COLUMN_SEPARATOR);
            }
            builder.append(cellText(cell));
        }
        return builder.toString().trim();
    }

    private static String joinSpans(List<InlineSpan> spans) {
        StringBuilder builder = new StringBuilder();
        for (InlineSpan span : spans) {
            builder.append(span.text());
        }
        return builder.toString();
    }
}
