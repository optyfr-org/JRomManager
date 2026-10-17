package jrm.fx.ui.misc;

import com.eclipsesource.json.JsonObject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jrm.aui.basic.AbstractSrcDstResult;
import jrm.aui.basic.SDRList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * A source/destination result with JavaFX properties for UI binding.
 * <p>
 * Extends {@link AbstractSrcDstResult} to add observable properties for source path,
 * destination path, result message, and selection state.
 *
 * @since 2.5
 */
public class SrcDstResult extends AbstractSrcDstResult {
    /**
     * The unique identifier.
     *
     * @param id the unique identifier
     * @return the unique identifier
     */
    private @Getter(onMethod_ = @Override) @Setter(value = AccessLevel.PROTECTED, onMethod_ = @Override) String id = null;
    /** The source path property. */
    private StringProperty src;
    /** The destination path property. */
    private StringProperty dst;
    /** The result message property. */
    private StringProperty result;
    /** The selection state property. */
    private BooleanProperty selected;

    /**
     * Constructs an empty source/destination result.
     */
    public SrcDstResult() {
        super();
    }

    /**
     * Constructs a source/destination result from JSON.
     *
     * @param jso the JSON object
     */
    public SrcDstResult(JsonObject jso) {
        super(jso);
    }

    /**
     * Returns the source path property.
     *
     * @return the source path property
     */
    public final StringProperty srcProperty() {
        if (this.src == null)
            this.src = new SimpleStringProperty();
        return this.src;
    }

    /**
     * Returns the source path.
     *
     * @return the source path
     */
    @Override
    public final String getSrc() {
        return this.srcProperty().get();
    }

    /**
     * Sets the source path.
     *
     * @param src the source path
     */
    @Override
    public final void setSrc(final String src) {
        this.srcProperty().set(src);
    }

    /**
     * Returns the destination path property.
     *
     * @return the destination path property
     */
    public final StringProperty dstProperty() {
        if (this.dst == null)
            this.dst = new SimpleStringProperty();
        return this.dst;
    }

    /**
     * Returns the destination path.
     *
     * @return the destination path
     */
    @Override
    public final String getDst() {
        return this.dstProperty().get();
    }

    /**
     * Sets the destination path.
     *
     * @param dst the destination path
     */
    @Override
    public final void setDst(final String dst) {
        this.dstProperty().set(dst);
    }

    /**
     * Returns the result message property.
     *
     * @return the result message property
     */
    public final StringProperty resultProperty() {
        if (this.result == null)
            this.result = new SimpleStringProperty("");
        return this.result;
    }

    /**
     * Returns the result message.
     *
     * @return the result message
     */
    @Override
    public final String getResult() {
        return this.resultProperty().get();
    }

    /**
     * Sets the result message.
     *
     * @param result the result message
     */
    @Override
    public final void setResult(final String result) {
        this.resultProperty().set(result);
    }

    /**
     * Returns the selection state property.
     *
     * @return the selection state property
     */
    public final BooleanProperty selectedProperty() {
        if (this.selected == null)
            this.selected = new SimpleBooleanProperty(true);
        return this.selected;
    }

    /**
     * Returns whether this row is selected.
     *
     * @return {@code true} if selected
     */
    @Override
    public final boolean isSelected() {
        return this.selectedProperty().get();
    }

    /**
     * Sets the selection state.
     *
     * @param selected {@code true} to select
     */
    @Override
    public final void setSelected(final boolean selected) {
        this.selectedProperty().set(selected);
    }

    /**
     * Deserializes a list of source/destination results from JSON.
     *
     * @param json the JSON string
     * @return the deserialized list of source/destination results
     */
    public static SDRList<SrcDstResult> fromJSON(String json) {
        return fromJSON(json, SrcDstResult.class);
    }
}
