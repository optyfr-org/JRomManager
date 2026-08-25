package jrm.fx.ui.profile.filter;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;
import jrm.fx.ui.misc.Settings;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;
import jrm.profile.filter.Keywords.KFCallBack;
import jrm.security.Session;
import jrm.security.Sessions;
import lombok.Getter;

/**
 * FXML controller for the keywords filter dialog.
 * <p>
 * Manages two list views (available and used keywords) with drag-and-drop support
 * for reordering. Persists window state and invokes the callback when keywords change.
 *
 * @since 2.5
 */
public class KeywordsController implements Initializable {

    /**
     * The keywords filter dialog scene.
     * @return the scene
     */
    @FXML
    @Getter
    Scene sceneKW;
    /** The FXML-injected list view of available keywords. */
    @FXML
    ListView<String> listAvailKW;
    /** The FXML-injected list view of used keywords. */
    @FXML
    ListView<String> listUsedKW;

    /** The current user session. */
    private Session session;
    /** The callback to invoke when keywords change. */
    KFCallBack callback;
    /** The anyware list being filtered. */
    AnywareList<? extends Anyware> awlist;

    /**
     * Initializes the controller: sets up the user session and configures
     * drag-and-drop event handlers for both list views.
     *
     * @param location  the FXML location
     * @param resources the resource bundle
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        session = Sessions.getSingleSession();

        EventHandler<MouseEvent> dragDetected = event -> {
            @SuppressWarnings("unchecked")
            ListView<String> list = (ListView<String>) event.getSource();
            Dragboard db = list.startDragAndDrop(TransferMode.ANY);

            ClipboardContent content = new ClipboardContent();
            content.putString(list.getSelectionModel().getSelectedItem());
            db.setContent(content);

            event.consume();
        };

        EventHandler<DragEvent> dragOver = event -> {
            if (event.getGestureSource() != event.getTarget() && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }

            event.consume();
        };

        EventHandler<DragEvent> dragDropped = event -> {
            @SuppressWarnings("unchecked")
            ListView<String> list = (ListView<String>) event.getGestureTarget();

            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                list.getItems().add(db.getString());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        };

        EventHandler<DragEvent> dragDone = event -> {
            if (event.getTransferMode() == TransferMode.MOVE) {
                @SuppressWarnings("unchecked")
                ListView<String> list = (ListView<String>) event.getGestureSource();
                list.getItems().remove(event.getDragboard().getString());
            }
            event.consume();
        };

        listAvailKW.setOnDragDetected(dragDetected);
        listAvailKW.setOnDragOver(dragOver);
        listAvailKW.setOnDragDropped(dragDropped);
        listAvailKW.setOnDragDone(dragDone);

        listUsedKW.setOnDragDetected(dragDetected);
        listUsedKW.setOnDragOver(dragOver);
        listUsedKW.setOnDragDropped(dragDropped);
        listUsedKW.setOnDragDone(dragDone);

    }

    /**
     * Closes the keywords dialog, persisting window position to settings.
     */
    @FXML
    public void onClose() {
        final var stage = (Stage) sceneKW.getWindow();
        session.getUser().getSettings().setProperty("Keywords.Bounds", Settings.toJson(stage));
        stage.hide();
    }

    /**
     * Applies the current keyword filter and invokes the callback.
     */
    @FXML
    public void onFilter() {
        callback.call(awlist, listUsedKW.getItems());
        sceneKW.getWindow().hide();
    }

    /**
     * Initializes the available keywords list with the given array.
     *
     * @param keywords the keywords to populate the available list
     */
    void initKeywords(String[] keywords) {
        listAvailKW.setItems(FXCollections.observableArrayList(keywords));
    }

}
