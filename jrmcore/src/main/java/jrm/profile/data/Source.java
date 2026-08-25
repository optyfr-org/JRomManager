package jrm.profile.data;

import lombok.Getter;

/**
 * A Source defines an origin metadata DAT source database used for importing files or generating profiles. It tracks instances
 * counts and maps name segments to properties keys.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class Source implements PropertyStub {
    /**
     * Name of the source file or database.
     * 
     * @return the name of the source
     */
    private final @Getter String name;

    /**
     * Instance count of games/systems referencing this source.
     * 
     * @return the count of references
     */
    private @Getter int count = 1;

    /**
     * Resolved property name linked to profile configurations.
     */
    private final String propname;

    /**
     * Constructs a Source instance with a normalized property name mapping.
     * 
     * @param name the non-null name of the source
     */
    public Source(String name) {
        this.name = name;
        final int lastDot = name.lastIndexOf('.');
        this.propname = "filter.sources." + name.replace('/', '_').substring(0, lastDot > 0 ? lastDot : name.length());
    }

    /**
     * Increments the reference count of this source.
     * 
     * @return this Source instance for chaining
     */
    public Source inc() {
        count++;
        return this;
    }

    /**
     * Get the property name of the current class.
     * 
     * @return the property name
     */
    @Override
    public String getPropertyName() {
        return propname;
    }

    /**
     * Format into a user-readable string containing the source name and its reference count.
     * 
     * @return formatted description string
     */
    @Override
    public String toString() {
        return name + " (" + count + ")";
    }
}
