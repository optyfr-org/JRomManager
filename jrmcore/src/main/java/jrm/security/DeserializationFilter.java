/*
 * Copyright (C) 2024 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.security;

import java.io.ObjectInputFilter;

import jrm.misc.Log;

/**
 * Provides deserialization filtering to prevent arbitrary code execution attacks.
 * This filter implements an allowlist approach, only permitting safe classes from
 * the application and standard Java libraries to be deserialized.
 * 
 * @author optyfr
 * @since 1.0
 */
public final class DeserializationFilter {
    
    /**
     * Maximum allowed object graph depth to prevent stack overflow attacks.
     */
    private static final int MAX_DEPTH = 100;
    
    /**
     * Maximum allowed array length to prevent memory exhaustion attacks.
     */
    private static final int MAX_ARRAY_LENGTH = 100000;
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private DeserializationFilter() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Creates a deserialization filter with the default depth limit.
     * <p>
     * This prevents arbitrary code execution via malicious serialized objects by implementing an allowlist of permitted classes
     * and rejecting all others.
     * </p>
     * 
     * The filter allows:
     * <ul>
     * <li>Java standard library classes (java.lang.*, java.util.*, java.math.*, java.time.*, and java.io.File)</li>
     * <li>Primitive arrays and arrays of allowed classes</li>
     * <li>Application classes from jrm.* packages</li>
     * <li>TorrentZip status classes from jtrrntzip.*</li>
     * </ul>
     * 
     * The filter rejects:
     * <ul>
     * <li>Known gadget classes that could be used in deserialization attacks</li>
     * <li>Any third-party library classes not explicitly allowed</li>
     * <li>Objects exceeding depth or array size limits (DoS prevention)</li>
     * </ul>
     * 
     * @return ObjectInputFilter configured with allowlist of safe classes and the default depth limit
     * 
     * @see #createFilter(int)
     */
    public static ObjectInputFilter createFilter() {
        return createFilter(MAX_DEPTH);
    }
    
    /**
     * Creates a deserialization filter with a caller-specific depth limit.
     * <p>
     * Different persisted formats can legitimately have very different object-graph depths (for example, torrent-check reports
     * nest one {@code TrntChkReport.Child} per piece). Persisted formats that need a higher limit can call this overload
     * instead of the default.
     * </p>
     * 
     * @param maxDepth the maximum object graph depth to allow before rejecting the stream
     * @return ObjectInputFilter configured with allowlist of safe classes and the supplied depth limit
     */
    public static ObjectInputFilter createFilter(final int maxDepth) {
        return filterInfo -> {
            Class<?> serialClass = filterInfo.serialClass();
            
            // Reject if depth or array size exceeds reasonable limits (DoS prevention)
            if (filterInfo.depth() > maxDepth || filterInfo.arrayLength() > MAX_ARRAY_LENGTH) {
                Log.warn(() -> String.format("Deserialization rejected: depth=%d (max=%d), arrayLength=%d (max=%d)",
                    filterInfo.depth(), maxDepth, filterInfo.arrayLength(), MAX_ARRAY_LENGTH));
                return ObjectInputFilter.Status.REJECTED;
            }
            
            // Allow null and primitive types
            if (serialClass == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }
            
            String className = serialClass.getName();
            
            if (isAllowedClassName(className)) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            
            // Reject everything else (including known gadget classes)
            Log.warn(() -> "Deserialization rejected for untrusted class: " + className);
            return ObjectInputFilter.Status.REJECTED;
        };
    }
    
    /**
     * Determines whether a class name (including array types) is allowed to be deserialized.
     * For reference arrays, the component type is checked against the allowlist.
     * 
     * @param className the fully qualified class name produced by {@link Class#getName()}
     * @return {@code true} if the class is allowed, {@code false} otherwise
     */
    private static boolean isAllowedClassName(String className) {
        // Strip array prefix(es) and check the underlying component type
        while (className.startsWith("[")) { //$NON-NLS-1$
            if (className.length() < 2) {
                return false;
            }
            final char type = className.charAt(1);
            if (type == 'L') {
                // Object reference array: remove the leading "[L" and trailing ";"
                className = className.substring(2, className.length() - 1);
                break;
            }
            if (type == '[') {
                // Multi-dimensional array: continue peeling
                className = className.substring(1);
            } else {
                // Primitive array
                return true;
            }
        }
        return isAllowedNonArrayClassName(className);
    }
    
    /**
     * Determines whether a non-array class name is allowed to be deserialized.
     * 
     * @param className the fully qualified class name (without array prefix)
     * @return {@code true} if the class is allowed, {@code false} otherwise
     */
    private static boolean isAllowedNonArrayClassName(String className) {
        // Allow JDK base classes needed for collections, basic types, file paths, and timestamps
        return className.startsWith("java.lang.") || //$NON-NLS-1$
            className.startsWith("java.util.") || //$NON-NLS-1$
            className.startsWith("java.math.") || //$NON-NLS-1$
            className.startsWith("java.time.") || //$NON-NLS-1$
            className.equals("java.io.File") || //$NON-NLS-1$
            // Allow application-specific classes from jrm package and subpackages
            className.startsWith("jrm.") || //$NON-NLS-1$
            // Allow torrentzip status types used in container caches
            className.startsWith("jtrrntzip."); //$NON-NLS-1$
    }
}
