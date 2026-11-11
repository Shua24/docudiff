package dev.docudiff.document;

import java.util.List;

/**
 * One ordered piece of a document. Formats map their own structures onto these blocks so the
 * diff engine only ever sees this shape.
 */
public sealed interface Block permits Block.Paragraph, Block.Heading, Block.CodeBlock {

    /** A run of inline text that belongs together on one line. */
    record Paragraph(List<InlineSpan> spans) implements Block {
        public Paragraph {
            spans = List.copyOf(spans);
        }
    }

    /** A heading with its level, 1 for a top-level heading. */
    record Heading(int level, List<InlineSpan> spans) implements Block {
        public Heading {
            spans = List.copyOf(spans);
        }
    }

    /** A fenced code block: its lines are compared verbatim, never reflowed. */
    record CodeBlock(String language, List<String> lines) implements Block {
        public CodeBlock {
            lines = List.copyOf(lines);
        }
    }
}
