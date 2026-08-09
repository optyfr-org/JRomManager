/*
 * Copyright (C) 2024 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.Set;

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
     * Allowlist breadth used when opening a filtered object stream.
     */
    public enum Mode {
        /**
         * Full application allowlist ({@code jrm.*}, {@code jtrrntzip.*}, broad JDK prefixes).
         * Used for profile caches, DirScan caches, and ProfileNFO.
         */
        DEFAULT,
        /**
         * Stricter report allowlist: only report/batch/data packages plus an explicit JDK class set.
         * Used for {@code Report}, {@code TrntChkReport}, and {@code DirUpdaterResults}.
         */
        REPORT
    }
    
    /**
     * Maximum allowed object graph depth to prevent stack overflow attacks.
     */
    private static final int MAX_DEPTH = 100;
    
    /**
     * Maximum allowed array length to prevent memory exhaustion attacks.
     */
    private static final int MAX_ARRAY_LENGTH = 100000;

    /**
     * Package prefixes permitted in {@link Mode#REPORT} for application types.
     */
    private static final String[] REPORT_APP_PREFIXES = {
        "jrm.profile.report.", //$NON-NLS-1$
        "jrm.batch.", //$NON-NLS-1$
        "jrm.profile.data.", //$NON-NLS-1$
        "jrm.misc.", //$NON-NLS-1$
        "jtrrntzip." //$NON-NLS-1$
    };

    /**
     * Explicit JDK classes permitted in {@link Mode#REPORT}.
     */
    private static final Set<String> REPORT_JDK_CLASSES = Set.of(
        "java.util.ArrayList",
        "java.util.HashMap",
        "java.util.HashSet",
        "java.util.LinkedHashMap",
        "java.util.LinkedHashSet",
        "java.util.TreeMap",
        "java.util.TreeSet",
        "java.util.EnumSet",
        "java.util.EnumMap",
        "java.util.Collections$UnmodifiableCollection",
        "java.util.Collections$UnmodifiableList",
        "java.util.Collections$UnmodifiableSet",
        "java.util.Collections$UnmodifiableMap",
        "java.util.Collections$EmptyList",
        "java.util.Collections$EmptySet",
        "java.util.Collections$EmptyMap",
        "java.util.Collections$SingletonList",
        "java.util.Collections$SingletonSet",
        "java.util.Collections$SingletonMap",
        "java.util.concurrent.atomic.AtomicInteger",
        "java.util.concurrent.atomic.AtomicLong",
        "java.util.Arrays$ArrayList",
        "java.lang.String",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Boolean",
        "java.lang.Double",
        "java.lang.Float",
        "java.lang.Byte",
        "java.lang.Short",
        "java.lang.Character",
        "java.lang.Enum",
        "java.lang.Number",
        "java.time.LocalDateTime",
        "java.time.Instant",
        "java.time.ZoneId",
        "java.time.ZoneOffset",
        "java.time.Ser",
        "java.io.File" //NOSONAR
    );

    /**
     * JDK package prefixes still needed for nested collection/map nodes in {@link Mode#REPORT}.
     */
    private static final String[] REPORT_JDK_PREFIXES = {
        "java.util.", //$NON-NLS-1$
        "java.lang.", //$NON-NLS-1$
        "java.time.", //$NON-NLS-1$
        "java.math." //$NON-NLS-1$
    };
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private DeserializationFilter() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Creates a deserialization filter with the default depth limit and {@link Mode#DEFAULT} allowlist.
     * 
     * @return ObjectInputFilter configured with allowlist of safe classes and the default depth limit
     * 
     * @see #createFilter(int)
     * @see #createFilter(Mode, int)
     */
    public static ObjectInputFilter createFilter() {
        return createFilter(Mode.DEFAULT, MAX_DEPTH);
    }
    
    /**
     * Creates a deserialization filter with a caller-specific depth limit and {@link Mode#DEFAULT} allowlist.
     * 
     * @param maxDepth the maximum object graph depth to allow before rejecting the stream
     * @return ObjectInputFilter configured with allowlist of safe classes and the supplied depth limit
     */
    public static ObjectInputFilter createFilter(final int maxDepth) {
        return createFilter(Mode.DEFAULT, maxDepth);
    }

    /**
     * Creates a deserialization filter with the default depth limit and the supplied allowlist mode.
     *
     * @param mode the allowlist mode
     * @return ObjectInputFilter configured for the mode
     */
    public static ObjectInputFilter createFilter(final Mode mode) {
        return createFilter(mode, MAX_DEPTH);
    }

    /**
     * Creates a deserialization filter with a caller-specific depth limit and allowlist mode.
     * <p>
     * Different persisted formats can legitimately have very different object-graph depths (for example, torrent-check reports
     * nest one {@code TrntChkReport.Child} per piece). Persisted formats that need a higher limit can call this overload
     * instead of the default.
     * </p>
     * 
     * @param mode the allowlist mode ({@link Mode#DEFAULT} or stricter {@link Mode#REPORT})
     * @param maxDepth the maximum object graph depth to allow before rejecting the stream
     * @return ObjectInputFilter configured with the selected allowlist and depth limit
     */
    public static ObjectInputFilter createFilter(final Mode mode, final int maxDepth) {
        final Mode effectiveMode = mode == null ? Mode.DEFAULT : mode;
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

            if (serialClass.isPrimitive()) {
                return ObjectInputFilter.Status.ALLOWED;
            }

            if (serialClass.isEnum()) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            
            String className = serialClass.getName();
            
            if (isAllowedClassName(effectiveMode, className)) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            
            // Reject everything else (including known gadget classes)
            Log.warn(() -> "Deserialization rejected for untrusted class: " + className);
            return ObjectInputFilter.Status.REJECTED;
        };
    }

    /**
     * Opens an {@link ObjectInputStream} with the default deserialization filter already applied.
     *
     * @param in the underlying input stream
     * @return a filtered object input stream
     * @throws IOException if the stream cannot be opened
     */
    public static ObjectInputStream openObjectInputStream(final InputStream in) throws IOException {
        return openObjectInputStream(in, Mode.DEFAULT, MAX_DEPTH);
    }

    /**
     * Opens an {@link ObjectInputStream} with a caller-specific depth limit already applied.
     *
     * @param in the underlying input stream
     * @param maxDepth the maximum object graph depth to allow
     * @return a filtered object input stream
     * @throws IOException if the stream cannot be opened
     */
    public static ObjectInputStream openObjectInputStream(final InputStream in, final int maxDepth) throws IOException {
        return openObjectInputStream(in, Mode.DEFAULT, maxDepth);
    }

    /**
     * Opens an {@link ObjectInputStream} with the supplied allowlist mode and default depth limit.
     *
     * @param in the underlying input stream
     * @param mode the allowlist mode
     * @return a filtered object input stream
     * @throws IOException if the stream cannot be opened
     */
    public static ObjectInputStream openObjectInputStream(final InputStream in, final Mode mode) throws IOException {
        return openObjectInputStream(in, mode, MAX_DEPTH);
    }

    /**
     * Opens an {@link ObjectInputStream} with the supplied allowlist mode and depth limit.
     *
     * @param in the underlying input stream
     * @param mode the allowlist mode
     * @param maxDepth the maximum object graph depth to allow
     * @return a filtered object input stream
     * @throws IOException if the stream cannot be opened
     */
    public static ObjectInputStream openObjectInputStream(final InputStream in, final Mode mode, final int maxDepth) throws IOException {
        final var ois = new ObjectInputStream(in);
        ois.setObjectInputFilter(createFilter(mode, maxDepth));
        return ois;
    }
    
    /**
     * Determines whether a class name (including array types) is allowed to be deserialized.
     * For reference arrays, the component type is checked against the allowlist.
     * 
     * @param mode the allowlist mode
     * @param className the fully qualified class name produced by {@link Class#getName()}
     * @return {@code true} if the class is allowed, {@code false} otherwise
     */
    private static boolean isAllowedClassName(final Mode mode, String className) {
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
        return isAllowedNonArrayClassName(mode, className);
    }
    
    /**
     * Determines whether a non-array class name is allowed to be deserialized.
     * 
     * @param mode the allowlist mode
     * @param className the fully qualified class name (without array prefix)
     * @return {@code true} if the class is allowed, {@code false} otherwise
     */
    private static boolean isAllowedNonArrayClassName(final Mode mode, final String className) {
        if (mode == Mode.REPORT) {
            return isAllowedReportClassName(className);
        }
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

    /**
     * Report-mode allowlist: narrower application prefixes plus an explicit JDK set,
     * with package prefixes retained for nested collection/map implementation classes.
     *
     * @param className the fully qualified class name (without array prefix)
     * @return {@code true} if the class is allowed under report mode
     */
    private static boolean isAllowedReportClassName(final String className) {
        if (className.equals("java.io.File")) { //$NON-NLS-1$
            return true;
        }
        if (REPORT_JDK_CLASSES.contains(className)) {
            return true;
        }
        for (final String prefix : REPORT_APP_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        // Nested HashMap$Node / ArrayList elements and similar internal JDK types
        for (final String prefix : REPORT_JDK_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
