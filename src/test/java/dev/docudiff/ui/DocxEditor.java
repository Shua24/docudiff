package dev.docudiff.ui;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Not part of the app. Rewrites a docx so it differs from the original in exactly the ways the
 * diff needs to demonstrate: one reworded word, one formatting-only change, one added paragraph.
 */
final class DocxEditor {

    private DocxEditor() {
    }

    static byte[] reword(byte[] original) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(original));
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text.contains("jumps")) {
                    replaceParagraphText(paragraph, text.replace("jumps", "leaps"));
                } else if (text.startsWith("Costs remained")) {
                    // Same words, now bold: a formatting-only change.
                    replaceParagraphText(paragraph, text);
                    for (XWPFRun run : paragraph.getRuns()) {
                        run.setBold(true);
                    }
                }
            }
            XWPFParagraph added = document.createParagraph();
            added.createRun().setText(
                    "Risk register reviewed and signed off by the finance committee.");
            document.write(buffer);
            return buffer.toByteArray();
        }
    }

    private static void replaceParagraphText(XWPFParagraph paragraph, String newText) {
        for (int index = paragraph.getRuns().size() - 1; index >= 0; index--) {
            paragraph.removeRun(index);
        }
        paragraph.createRun().setText(newText);
    }
}
