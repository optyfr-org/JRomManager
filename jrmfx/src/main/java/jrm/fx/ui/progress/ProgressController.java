package jrm.fx.ui.progress;

import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import jrm.fx.ui.MainFrame;
import jrm.fx.ui.progress.ProgressTask.PData;
import jrm.fx.ui.status.NeutralToNodeFormatter;
import jrm.locale.Messages;

/**
 * FXML controller for the progress dialog.
 * <p>
 * Manages up to three progress bars (main, sub, sub-sub) with labels and time-left
 * indicators. Dynamically adds/removes thread-specific progress panels based on
 * the task's thread count.
 *
 * @since 2.5
 */
public class ProgressController implements Initializable {
    /** The main panel container. */
    @FXML
    private VBox panel;
    /** The primary progress bar. */
    @FXML
    private ProgressBar progressBar;
    /** The primary progress label. */
    @FXML
    private Label progressBarLbl;
    /** The primary time-left label. */
    @FXML
    private Label lblTimeleft;
    /** The secondary progress bar. */
    @FXML
    private ProgressBar progressBar2;
    /** The secondary progress label. */
    @FXML
    private Label progressBarLbl2;
    /** The secondary time-left label. */
    @FXML
    private Label lblTimeleft2;
    /** The tertiary progress bar. */
    @FXML
    private ProgressBar progressBar3;
    /** The tertiary progress label. */
    @FXML
    private Label progressBarLbl3;
    /** The tertiary time-left label. */
    @FXML
    private Label lblTimeleft3;
    /** The cancel button. */
    @FXML
    private Button cancelBtn;

    /**
     * The progress task being tracked.
     */
    private ProgressTask<?> task;

    /** Last applied info strings, used to skip unchanged SAX/node rebuilds. */
    private String[] lastInfos = new String[0];

    /** Last applied sub-info strings, used to skip unchanged SAX/node rebuilds. */
    private String[] lastSubinfos = new String[0];

    /** Last applied sub-info layout mode, used to choose extend vs rebuild. */
    private Boolean lastMultipleSubInfos = false;

    /**
     * Binds this controller to a progress task's coalesced data property.
     *
     * @param task the progress task
     */
    public void setTask(ProgressTask<?> task) {
        this.task = task;
        task.progressDataProperty().addListener((_, _, pd) -> {
            if (pd != null)
                applyProgress(pd);
        });
        final PData current = task.progressDataProperty().get();
        if (current != null)
            applyProgress(current);
    }

    /**
     * Applies layout then values from a snapshot. No-op if the dialog is already closed.
     *
     * @param pd the progress snapshot
     */
    private void applyProgress(PData pd) {
        if (isWindowGone())
            return;
        if (pd.getThreadCnt() > lblInfo.length && Objects.equals(pd.getMultipleSubInfos(), lastMultipleSubInfos))
            extendInfos(pd.getThreadCnt(), pd.getMultipleSubInfos());
        else
            setInfos(pd.getThreadCnt(), pd.getMultipleSubInfos());
        setFullProgress(pd);
    }

    /**
     * Returns whether the progress window has been hidden or detached.
     *
     * @return {@code true} if updates should be ignored
     */
    private boolean isWindowGone() {
        final var scene = panel.getScene();
        if (scene == null)
            return true;
        final var window = scene.getWindow();
        return window == null || !window.isShowing();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Initializes the progress dialog: configures the cancel button icon,
     * sets an initial single-thread info panel, and hides the secondary
     * and tertiary progress bars.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cancelBtn.setGraphic(new ImageView(MainFrame.getIcon("/jrm/resicons/icons/stop.png")));
        setInfos(1, false);
        progressBar2.setVisible(false);
        progressBarLbl2.setVisible(false);
        lblTimeleft2.setVisible(false);
        ((GridPane) lblTimeleft2.getParent()).getRowConstraints().get(2).setPrefHeight(0);
        progressBar3.setVisible(false);
        progressBarLbl3.setVisible(false);
        lblTimeleft3.setVisible(false);
        ((GridPane) lblTimeleft3.getParent()).getRowConstraints().get(3).setPrefHeight(0);
        panel.setSpacing(3);
    }

    /** Placeholder for unknown elapsed/total time. */
    private static final String HH_MM_SS_OF_HH_MM_SS_NONE = "--:--:-- / --:--:--";

    /** Thread-info panels, one per thread. */
    private Pane[] lblInfo = new Pane[0];

    /** Sub-info panels, one per thread when multiple sub-infos are enabled. */
    private Pane[] lblSubInfo = new Pane[0];

    /** Background colour for even-indexed rows. */
    private static final Color colorNormal = new Color(0.7, 0.7, 0.7, 1.0);
    /** Background colour for odd-indexed rows. */
    private static final Color colorLight = new Color(0.8, 0.8, 0.8, 1.0);
    /** Background colour for the single sub-info row. */
    private static final Color colorLighter = new Color(0.9, 0.9, 0.9, 1.0);

    /**
     * Configures the progress panels for the given thread count.
     * <p>
     * Called by {@link ProgressTask#setInfos(int, Boolean)}.
     *
     * @param threadCnt        the number of threads
     * @param multipleSubInfos whether to show multiple sub-info panels
     */
    void setInfos(int threadCnt, Boolean multipleSubInfos) {
        final var lblSubInfoCnt = multipleSubInfos == null ? 0 : (multipleSubInfos ? threadCnt : 1);

        if (lblInfo != null && lblInfo.length == threadCnt && lblSubInfo != null && lblSubInfo.length == lblSubInfoCnt) {
            lastMultipleSubInfos = multipleSubInfos;
            return;
        }

        lastInfos = new String[0];
        lastSubinfos = new String[0];

        recyclePanelViews();

        lblInfo = new Pane[threadCnt];
        lblSubInfo = new Pane[lblSubInfoCnt];

        for (int i = 0; i < threadCnt; i++)
            addThreadRow(i, isOdd(i) ? colorNormal : colorLight, Boolean.TRUE.equals(multipleSubInfos));
        if (Boolean.FALSE.equals(multipleSubInfos)) {
            lblSubInfo[0] = buildView(colorLighter);
            if (threadCnt == 1) {
                placeSubInfoBeside(lblInfo[0], lblSubInfo[0]);
            } else {
                panel.getChildren().add(lblSubInfo[0]);
            }
        }
        lastMultipleSubInfos = multipleSubInfos;
    }

    /**
     * Extends the info and sub-info arrays to accommodate additional threads.
     * New panels are created and appended to the layout.
     * <p>
     * Called by {@link ProgressTask}.
     *
     * @param threadCnt        the new total thread count
     * @param multipleSubInfos whether to extend sub-info panels as well
     */
    void extendInfos(int threadCnt, Boolean multipleSubInfos) {
        if (lblInfo == null || lblInfo.length == threadCnt)
            return;

        if (Boolean.TRUE.equals(multipleSubInfos) && lblSubInfo == null)
            return;

        lastInfos = Arrays.copyOf(lastInfos, threadCnt);
        if (Boolean.TRUE.equals(multipleSubInfos))
            lastSubinfos = Arrays.copyOf(lastSubinfos, threadCnt);

        final var oldThreadCnt = lblInfo.length;

        lblInfo = Arrays.copyOf(lblInfo, threadCnt);
        if (Boolean.TRUE.equals(multipleSubInfos))
            lblSubInfo = Arrays.copyOf(lblSubInfo, threadCnt);

        for (int i = oldThreadCnt; i < threadCnt; i++)
            addThreadRow(i, isOdd(i) ? colorNormal : colorLight, Boolean.TRUE.equals(multipleSubInfos));
        lastMultipleSubInfos = multipleSubInfos;
    }

    /**
     * Checks the parity of an integer.
     *
     * @param i the integer to test
     * @return {@code true} if {@code i} is odd, {@code false} if even
     */
    private boolean isOdd(int i) {
        return (i % 2) != 0;
    }

    /** Pool of recycled {@link HBox} containers to avoid repeated instantiation. */
    private Deque<HBox> viewCache = new ArrayDeque<>();

    /** Pool of recycled row {@link HBox} wrappers (info + sub-info side by side). */
    private Deque<HBox> rowCache = new ArrayDeque<>();

    /**
     * Builds or reuses an {@link HBox} view container with the given background colour.
     *
     * @param color the background colour
     * @return a styled {@link HBox} ready for content
     */
    private HBox buildView(Color color) {
        final HBox view;
        if (!viewCache.isEmpty())
            view = viewCache.poll();
        else {
            view = new HBox();
            view.setPrefHeight(20);
            view.setMaxWidth(Integer.MAX_VALUE);
            view.setAlignment(Pos.CENTER_LEFT);
        }
        view.setBackground(new Background(new BackgroundFill(color, null, null)));
        view.setBorder(new Border(new BorderStroke(color.darker(), color.brighter(), color.brighter(), color.darker(), BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID,
                BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID, null, null, null)));
        return view;
    }

    /**
     * Recycles info, sub-info, and row containers currently in {@link #panel}.
     */
    private void recyclePanelViews() {
        for (final var n : panel.getChildren())
            recycleBox(n);
        panel.getChildren().clear();
    }

    /**
     * Recycles an {@link HBox}, including nested info panes when it is a row wrapper.
     *
     * @param n the node to recycle
     */
    private void recycleBox(Node n) {
        if (!(n instanceof HBox box))
            return;
        final var nested = new ArrayList<HBox>();
        for (final var child : box.getChildren()) {
            if (child instanceof HBox inner)
                nested.add(inner);
        }
        if (!nested.isEmpty()) {
            box.getChildren().clear();
            for (final var inner : nested) {
                inner.getChildren().clear();
                viewCache.add(inner);
            }
            rowCache.add(box);
        } else {
            box.getChildren().clear();
            viewCache.add(box);
        }
    }

    /**
     * Builds or reuses a row {@link HBox} that holds info and sub-info side by side.
     *
     * @return an empty row container
     */
    private HBox buildRow() {
        if (!rowCache.isEmpty())
            return rowCache.poll();
        final var row = new HBox();
        row.setSpacing(1);
        row.setMaxWidth(Integer.MAX_VALUE);
        row.setFillHeight(true);
        return row;
    }

    /**
     * Adds a thread info row, optionally with a sub-info pane to its right.
     *
     * @param i           the thread index
     * @param color       the row background colour
     * @param withSubInfo whether to place a per-thread sub-info pane on the right
     */
    private void addThreadRow(int i, Color color, boolean withSubInfo) {
        lblInfo[i] = buildView(color);
        HBox.setHgrow(lblInfo[i], Priority.ALWAYS);
        lblInfo[i].setPrefWidth(100);
        if (withSubInfo) {
            lblSubInfo[i] = buildView(color);
            HBox.setHgrow(lblSubInfo[i], Priority.ALWAYS);
            lblSubInfo[i].setPrefWidth(100);
            final var row = buildRow();
            row.getChildren().addAll(lblInfo[i], lblSubInfo[i]);
            panel.getChildren().add(row);
        } else {
            panel.getChildren().add(lblInfo[i]);
        }
    }

    /**
     * Places a shared sub-info pane to the right of a single info pane.
     *
     * @param info    the info pane
     * @param subInfo the sub-info pane
     */
    private void placeSubInfoBeside(Pane info, Pane subInfo) {
        panel.getChildren().remove(info);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox.setHgrow(subInfo, Priority.ALWAYS);
        info.setPrefWidth(100);
        subInfo.setPrefWidth(100);
        final var row = buildRow();
        row.getChildren().addAll(info, subInfo);
        panel.getChildren().add(row);
    }

    /**
     * Clears all children from every info and sub-info panel.
     * <p>
     * Called by {@link ProgressTask#clearInfos()}.
     */
    void clearInfos() {
        for (final var label : lblInfo)
            label.getChildren().clear();
        for (final var label : lblSubInfo)
            label.getChildren().clear();
        lastInfos = new String[0];
        lastSubinfos = new String[0];
    }

    /**
     * Updates all progress panels from a progress data snapshot.
     *
     * @param pd the progress data containing info strings and progress bar states
     */
    public void setFullProgress(PData pd) {
        if (isWindowGone())
            return;
        lastInfos = applyInfoNodes(lblInfo, pd.getInfos(), lastInfos);
        lastSubinfos = applyInfoNodes(lblSubInfo, pd.getSubinfos(), lastSubinfos);
        updateProgressBar(progressBar, progressBarLbl, lblTimeleft, 1, new ProgressData(pd.getPb1().isVisibility(), pd.getPb1().isIndeterminate(), pd.getPb1().getVal() > 0, pd.getPb1().getPerc(), pd.getPb1().isStringPainted(), pd.getPb1().getMsg(), pd.getPb1().getTimeleft()));
        updateProgressBar(progressBar2, progressBarLbl2, lblTimeleft2, 2, new ProgressData(pd.getPb2().isVisibility(), pd.getPb2().isIndeterminate(), pd.getPb2().getPerc() >= 0, pd.getPb2().getPerc(), pd.getPb2().isStringPainted(), pd.getPb2().getMsg(), pd.getPb2().getTimeleft()));
        updateProgressBar(progressBar3, progressBarLbl3, lblTimeleft3, 3, new ProgressData(pd.getPb3().isVisibility(), pd.getPb3().isIndeterminate(), pd.getPb3().getPerc() >= 0, pd.getPb3().getPerc(), pd.getPb3().isStringPainted(), pd.getPb3().getMsg(), pd.getPb3().getTimeleft()));
    }

    /**
     * Rebuilds info pane children only when the string for that row changed.
     *
     * @param panes the info or sub-info panes
     * @param values the snapshot strings
     * @param lastApplied the previously applied strings
     * @return the applied strings for the next comparison
     */
    private String[] applyInfoNodes(Pane[] panes, String[] values, String[] lastApplied) {
        final var applied = Arrays.copyOf(lastApplied, panes.length);
        for (int i = 0; i < panes.length; i++) {
            final String next = i < values.length && values[i] != null ? values[i] : "";
            if (i < lastApplied.length && Objects.equals(next, lastApplied[i] == null ? "" : lastApplied[i]))
                continue;
            if (!applyPlainLabel(panes[i], next))
                panes[i].getChildren().setAll(NeutralToNodeFormatter.toNodes(next));
            applied[i] = next;
        }
        return applied;
    }

    /**
     * Updates a plain-text info row in place when the pane already holds a single {@link Label}.
     *
     * @param pane the info or sub-info pane
     * @param next the next plain string, never {@code null}
     * @return {@code true} if the row was updated without replacing children
     */
    private boolean applyPlainLabel(Pane pane, String next) {
        if (next.startsWith("<document>"))
            return false;
        final var children = pane.getChildren();
        if (children.size() == 1 && children.get(0) instanceof Label label) {
            if (!Objects.equals(next, label.getText()))
                label.setText(next);
            return true;
        }
        return next.isEmpty() && children.isEmpty();
    }

    /**
     * Immutable snapshot of a progress bar's state.
     *
     * @param visible       whether the bar is visible
     * @param indeterminate  whether the bar is indeterminate
     * @param hasProgress    whether the bar reports a valid progress value
     * @param perc           the progress percentage (0-100)
     * @param stringPainted  whether the progress string is shown
     * @param msg            the progress message, may be {@code null}
     * @param timeleftStr    the formatted time-left string
     */
    private record ProgressData(boolean visible, boolean indeterminate, boolean hasProgress, double perc, boolean stringPainted, String msg, String timeleftStr) {
    }

    /**
     * Updates one progress bar and its associated labels.
     *
     * @param bar     the progress bar control
     * @param barLbl  the label showing the progress string
     * @param timeleft the label showing time remaining
     * @param rowIndex the row in the parent {@link GridPane} for visibility toggling
     * @param data    the progress data to apply
     */
    private void updateProgressBar(ProgressBar bar, Label barLbl, Label timeleft, int rowIndex, ProgressData data) {
        final var visible = data.visible();
        if (bar.isVisible() != visible) {
            bar.setVisible(visible);
            barLbl.setVisible(visible);
            timeleft.setVisible(visible);
            ((GridPane) timeleft.getParent()).getRowConstraints().get(rowIndex).setPrefHeight(visible ? Region.USE_COMPUTED_SIZE : 0);
        }
        if (!visible)
            return;
        if (data.indeterminate()) {
            bar.setProgress(-1);
            barLbl.setVisible(false);
            return;
        }
        if (!data.hasProgress()) {
            timeleft.setText(HH_MM_SS_OF_HH_MM_SS_NONE);
            return;
        }
        if ((int) (bar.getProgress() * 100) != (int) data.perc())
            bar.setProgress(data.perc() / 100);
        if (data.stringPainted()) {
            barLbl.setVisible(true);
            final var msg = Optional.ofNullable(data.msg()).orElse("");
            if (!msg.equals(barLbl.getText()))
                barLbl.setText(msg);
        } else
            barLbl.setVisible(false);
        if (!Objects.equals(data.timeleftStr(), timeleft.getText()))
            timeleft.setText(data.timeleftStr());
    }

    /**
     * Hides the progress dialog window.
     */
    void close() {
        panel.getScene().getWindow().hide();
    }

    /**
     * Enables or disables the cancel button.
     *
     * @param canCancel {@code true} to enable the cancel button
     */
    void canCancel(boolean canCancel) {
        cancelBtn.setDisable(!canCancel);
    }

    /**
     * Initiates cancellation of the tracked task and disables the cancel button
     * to prevent duplicate requests.
     */
    @FXML
    void doCancel() {
        task.doCancel();
        cancelBtn.setDisable(true);
        cancelBtn.setText(Messages.getString("Progress.Canceling")); //$NON-NLS-1$
    }
}
