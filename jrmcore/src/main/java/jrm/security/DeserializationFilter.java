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
 * Provides deserialization filtering to prevent arbitrary code execution via malicious serialized objects.
 * <p>
 * This filter whitelists only expected JRM application classes and standard Java classes, rejecting all others.
 * This mitigates deserialization vulnerabilities where attackers could upload malicious serialized objects
 * to cache directories and trigger their deserialization.
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class DeserializationFilter {
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private DeserializationFilter() {
        // Utility class
    }
    
    /**
     * Creates a deserialization filter that whitelists only expected JRM application classes.
     * This prevents arbitrary code execution via malicious serialized objects.
     * <p>
     * The filter allows:
     * <ul>
     * <li>JRM application classes (jrm.profile.*, jrm.misc.*, jrm.aui.*, jrm.batch.*)</li>
     * <li>Standard Java collection and utility classes (java.util.*, java.lang.*, java.math.*)</li>
     * <li>Primitive arrays and arrays of allowed classes</li>
     * </ul>
     * All other classes are rejected and logged.
     * 
     * @return an ObjectInputFilter that accepts only safe classes
     */
    public static ObjectInputFilter createFilter() {
        return filterInfo -> {
            Class<?> serialClass = filterInfo.serialClass();
            if (serialClass == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }
            
            String className = serialClass.getName();
            
            // Allow JRM application classes
            if (className.startsWith("jrm.profile.") ||
                className.startsWith("jrm.misc.") ||
                className.startsWith("jrm.aui.") ||
                className.startsWith("jrm.batch.")) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            
            // Allow standard Java collection and utility classes
            if (className.startsWith("java.util.") ||
                className.startsWith("java.lang.") ||
                className.startsWith("java.math.") ||
                className.startsWith("java.time.") ||
                className.equals("[B") || // byte array
                className.equals("[C") || // char array
                className.equals("[I") || // int array
                className.equals("[J") || // long array
                className.equals("[Z") || // boolean array
                className.equals("[F") || // float array
                className.equals("[D") || // double array
                className.equals("[S") || // short array
                className.equals("[Ljava.lang.String;") || // String array
                className.startsWith("[Ljava.util.") || // arrays of collections
                className.startsWith("[Ljava.lang.") || // arrays of lang classes
                className.startsWith("[Ljava.math.") || // arrays of math classes
                className.startsWith("[Ljava.time.") || // arrays of time classes
                className.startsWith("[Ljrm.profile.") || // arrays of profile classes
                className.startsWith("[Ljrm.misc.") || // arrays of misc classes
                className.startsWith("[Ljrm.aui.") || // arrays of aui classes
                className.startsWith("[Ljrm.batch.")) { // arrays of batch classes
                return ObjectInputFilter.Status.ALLOWED;
            }
            
            // Reject all other classes
            Log.warn("Deserialization rejected for untrusted class: " + className);
            return ObjectInputFilter.Status.REJECTED;
        };
    }
}
