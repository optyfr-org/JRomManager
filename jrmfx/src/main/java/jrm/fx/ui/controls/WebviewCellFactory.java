package jrm.fx.ui.controls;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.effect.BlendMode;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;

/**
 * A table cell factory that renders HTML content in an embedded {@link WebView}.
 * <p>
 * The cell creates a new WebView (lazily on first content update) for each cell and loads
 * the HTML string as content. The WebView is styled with a transparent background and reduced font scale.
 *
 * @param <S> the type of the TableView items
 * @param <T> the type of the cell item
 * @since 2.5
 */
public class WebviewCellFactory<S, T> implements Callback<TableColumn<S, T>, TableCell<S, T>> {
    @Override
    public TableCell<S, T> call(TableColumn<S, T> column) {
        return new TableCell<S, T>() {
            /**
              * Updates the cell by loading the item as HTML content.
              * <p>
              * Empty or null items clear the cell; otherwise a {@link WebView} (created once per cell
              * on first use) is set as graphic and the content is loaded with transparent styling.
              *
              * @param item  the HTML content to render, or {@code null}
              * @param empty whether this cell represents an empty row
              */
            private WebView webview;
            private WebEngine engine;
            private T lastItem;

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    lastItem = null;
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }
                if (webview == null) {
                    webview = newWebView();
                    engine = webview.getEngine();
                }
                setGraphic(webview);
                if (item.equals(lastItem))
                    return;
                lastItem = item;
                engine.loadContent(
                        "<body topmargin=0 leftmargin=0 style=\"background-color: transparent;white-space:nowrap;overflow:hidden;text-overflow:ellipsis\">" + item + "</body>");
            }

            private static WebView newWebView() {
                final var view = new WebView();
                view.setPrefHeight(-1);
                view.setBlendMode(BlendMode.DARKEN);
                view.setFontScale(0.75);
                return view;
            }
        };
    }
}
