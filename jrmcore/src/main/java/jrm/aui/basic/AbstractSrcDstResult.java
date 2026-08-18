package jrm.aui.basic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import jrm.misc.Log;

/**
 * AbstractSrcDstResult is an abstract class that represents a source-destination result. It provides methods to set and get the
 * source, destination, result, and selection status. It also includes methods to convert the object to and from JSON format.
 */
public abstract class AbstractSrcDstResult {
    /**
     * Constructs an AbstractSrcDstResult object.
     */
    protected AbstractSrcDstResult() { /* default constructor */
    }

    /**
     * Constructs an AbstractSrcDstResult object from a JSON object.
     *
     * @param jso the JSON object to initialize the AbstractSrcDstResult
     */
    protected AbstractSrcDstResult(JsonObject jso) {
        fromJSONObject(jso);
    }

    /**
     * Converts a list of AbstractSrcDstResult objects to a JSON string.
     *
     * @param list the list of AbstractSrcDstResult objects to convert
     * 
     * @return a JSON string representation of the list
     */
    public static String toJSON(List<? extends AbstractSrcDstResult> list) {
        JsonArray array = Json.array();
        for (final var sdr : list)
            array.add(sdr.toJSONObject());
        return array.toString();
    }

    /**
     * Sets the selection status of the result.
     *
     * @param selected true if the result is selected, false otherwise
     */
    public abstract void setSelected(boolean selected);

    /**
     * Returns the selection status of the result.
     *
     * @return true if the result is selected, false otherwise
     */
    public abstract boolean isSelected();

    /**
     * Sets the result value.
     *
     * @param result the result value to set
     */
    public abstract void setResult(String result);

    /**
     * Returns the result value.
     *
     * @return the result value
     */
    public abstract String getResult();

    /**
     * Sets the destination value.
     *
     * @param dst the destination value to set
     */
    public abstract void setDst(String dst);

    /**
     * Returns the destination value.
     *
     * @return the destination value
     */
    public abstract String getDst();

    /**
     * Sets the source value.
     *
     * @param src the source value to set
     */
    public abstract void setSrc(String src);

    /**
     * Returns the source value.
     *
     * @return the source value
     */
    public abstract String getSrc();

    /**
     * Sets the ID of the result.
     *
     * @param id the ID to set
     */
    protected abstract void setId(String id);

    /**
     * Returns the ID of the result.
     *
     * @return the ID of the result
     */
    public abstract String getId();

    /**
     * Converts the AbstractSrcDstResult object to a JSON object.
     *
     * @return a JSON object representation of the AbstractSrcDstResult
     */
    public JsonObject toJSONObject() {
        JsonObject jso = Json.object();
        jso.add("id", Optional.ofNullable(getId()).orElse(UUID.randomUUID().toString())); //$NON-NLS-1$
        jso.add("src", getSrc()); //$NON-NLS-1$
        jso.add("dst", getDst()); //$NON-NLS-1$
        jso.add("result", getResult()); //$NON-NLS-1$
        jso.add("selected", isSelected()); //$NON-NLS-1$
        return jso;
    }

    /**
     * Populates the fields of this object from a JSON object.
     *
     * @param jso the JSON object to extract data from
     */
    public void fromJSONObject(JsonObject jso) {
        JsonValue lId = jso.get("id"); //$NON-NLS-1$
        if (isPresent(lId))
            setId(lId.asString());
        JsonValue lSrc = jso.get("src"); //$NON-NLS-1$
        if (isPresent(lSrc))
            setSrc(lSrc.asString());
        JsonValue lDst = jso.get("dst"); //$NON-NLS-1$
        if (isPresent(lDst))
            setDst(lDst.asString());
        JsonValue lResult = jso.get("result"); //$NON-NLS-1$
        if (isPresent(lResult))
            setResult(lResult.asString());
        setSelected(jso.getBoolean("selected", true)); //$NON-NLS-1$
    }

    private static boolean isPresent(JsonValue value) {
        return value != null && value != Json.NULL;
    }

    /**
     * Converts a JSON string to an SDRList of AbstractSrcDstResult objects.
     *
     * @param <T> the type of the AbstractSrcDstResult
     * @param json the JSON string to convert
     * @param cls the class of the AbstractSrcDstResult objects
     * 
     * @return an SDRList containing the AbstractSrcDstResult objects
     */
    public static <T extends AbstractSrcDstResult> SDRList<T> fromJSON(String json, Class<T> cls) {
        final var sdrl = new SDRList<T>();
        for (JsonValue arrv : Json.parse(json).asArray()) // $NON-NLS-1$ //$NON-NLS-2$
        {
            try {
                final var sdr = cls.getDeclaredConstructor(JsonObject.class).newInstance(arrv.asObject());
                if (sdr.getId() == null) {
                    sdrl.needSave = true;
                    sdr.setId(UUID.randomUUID().toString());
                }
                sdrl.add(sdr);
            } catch (Exception e) {
                Log.err(() ->e.getMessage() + " for " + json, e);
            }
        }
        return sdrl;
    }
}
