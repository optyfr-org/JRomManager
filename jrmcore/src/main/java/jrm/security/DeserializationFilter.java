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
     * Creates a deserialization filter that only allows safe classes from this application.
     * This prevents arbitrary code execution via malicious serialized objects by implementing
     * an allowlist of permitted classes and rejecting all others.
     * 
     * The filter allows:
     * <ul>
     * <li>Java standard library classes (java.lang.*, java.util.*, java.math.*)</li>
     * <li>Primitive arrays and String/Object arrays</li>
     * <li>Application classes from jrm.* packages</li>
     * </ul>
     * 
     * The filter rejects:
     * <ul>
     * <li>Known gadget classes that could be used in deserialization attacks</li>
     * <li>Any third-party library classes not explicitly allowed</li>
     * <li>Objects exceeding depth or array size limits (DoS prevention)</li>
     * </ul>
     * 
     * @return ObjectInputFilter configured with allowlist of safe classes
     */
    public static ObjectInputFilter createFilter() {
        return filterInfo -> {
            Class<?> serialClass = filterInfo.serialClass();
            
            // Reject if depth or array size exceeds reasonable limits (DoS prevention)
            if (filterInfo.depth() > MAX_DEPTH || filterInfo.arrayLength() > MAX_ARRAY_LENGTH) {
                Log.warn(() -> String.format("Deserialization rejected: depth=%d (max=%d), arrayLength=%d (max=%d)",
                    filterInfo.depth(), MAX_DEPTH, filterInfo.arrayLength(), MAX_ARRAY_LENGTH));
                return ObjectInputFilter.Status.REJECTED;
            }
            
            // Allow null and primitive types
            if (serialClass == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }
            
            String className = serialClass.getName();
            
            // Allow JDK base classes needed for collections and basic types
            if (className.startsWith("java.lang.") ||
                className.startsWith("java.util.") ||
                className.startsWith("java.math.") ||
                className.equals("[B") || // byte array
                className.equals("[I") || // int array
                className.equals("[J") || // long array
                className.equals("[S") || // short array
                className.equals("[C") || // char array
                className.equals("[F") || // float array
                className.equals("[D") || // double array
                className.equals("[Z") || // boolean array
                className.equals("[Ljava.lang.String;") || // String array
                className.equals("[Ljava.lang.Object;")) { // Object array
                return ObjectInputFilter.Status.ALLOWED;
            }
            
            // Allow only application-specific classes from jrm package and subpackages
            if (className.startsWith("jrm.")) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            
            // Reject everything else (including known gadget classes)
            Log.warn(() -> "Deserialization rejected for untrusted class: " + className);
            return ObjectInputFilter.Status.REJECTED;
        };
    }
}
