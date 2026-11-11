package dev.docudiff.ui;

import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Renders markdown into the WebView, with a small stylesheet that matches the app's surface.
 *
 * <p>Called only from the JavaFX thread; loading HTML is asynchronous, so the pane shows the
 * previous content until the new document reports SUCCEEDED.
 */
public final class MarkdownViewRenderer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

    private static final String PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                body { font-family: 'Liberation Sans', system-ui, sans-serif;
                       font-size: 15px; line-height: 1.6; color: #1f2430;
                       max-width: 860px; margin: 24px auto; padding: 0 24px; }
                h1, h2, h3, h4 { color: #1b3a4b; margin-top: 1.4em; }
                h1 { border-bottom: 2px solid #cfe3ea; padding-bottom: 6px; }
                code { background: #eef3f6; padding: 1px 4px; border-radius: 3px;
                       font-family: 'Liberation Mono', monospace; font-size: 92%; }
                pre { background: #eef3f6; padding: 12px; border-radius: 6px; overflow-x: auto; }
                blockquote { border-left: 3px solid #9fc4d0; margin-left: 0; padding-left: 14px;
                             color: #4a5568; }
                table { border-collapse: collapse; }
                th, td { border: 1px solid #cfe3ea; padding: 6px 10px; }
              </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """;

    private final WebView webView = new WebView();

    public MarkdownViewRenderer() {
        webView.setContextMenuEnabled(false);
    }

    public javafx.scene.Node node() {
        return new StackPane(webView);
    }

    public void render(String markdown) {
        Node document = PARSER.parse(markdown);
        String bodyHtml = HTML_RENDERER.render(document);
        String page = PAGE_TEMPLATE.formatted(bodyHtml);
        webView.getEngine().loadContent(page);
    }

    /** True once the current page has finished loading. */
    public boolean isLoaded() {
        return webView.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED;
    }
}
