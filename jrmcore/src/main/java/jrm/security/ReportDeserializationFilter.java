package jrm.security;

import java.io.ObjectInputFilter;
import java.util.Set;

/**
 * Deserialization filter for report cache files to prevent arbitrary code execution via malicious serialized objects.
 * <p>
 * This filter implements a strict allowlist of classes that are permitted during deserialization of cached report files.
 * It protects against deserialization attacks by rejecting any classes not explicitly approved for report serialization.
 * </p>
 * <p>
 * The filter allows:
 * <ul>
 * <li>Core report classes from jrm.profile.report and jrm.batch packages</li>
 * <li>Standard Java collection classes (ArrayList, HashMap, HashSet, etc.)</li>
 * <li>Java primitive types and arrays</li>
 * <li>Enum types</li>
 * </ul>
 * All other classes are rejected to prevent gadget chain exploitation.
 * </p>
 *
 * @author JRM Security Team
 * 
 * @since 2.0
 */
public final class ReportDeserializationFilter implements ObjectInputFilter {
    
    /**
     * Set of allowed class name prefixes for deserialization.
     * <p>
     * These prefixes cover the legitimate classes used in report serialization while blocking potentially dangerous classes.
     * </p>
     */
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
        "jrm.profile.report.",
        "jrm.batch.",
        "jrm.profile.data.",
        "jrm.misc.",
        "java.lang.",
        "java.util.",
        "java.time.",
        "java.io.",
        "[L",  // Object arrays
        "[I",  // int arrays
        "[J",  // long arrays
        "[Z",  // boolean arrays
        "[B",  // byte arrays
        "[C",  // char arrays
        "[S",  // short arrays
        "[F",  // float arrays
        "[D"   // double arrays
    );
    
    /**
     * Set of explicitly allowed collection and utility classes.
     * <p>
     * These are standard Java classes commonly used in serialization that are considered safe.
     * </p>
     */
    private static final Set<String> ALLOWED_CLASSES = Set.of(
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
        "java.util.concurrent.atomic.AtomicInteger",
        "java.util.concurrent.atomic.AtomicLong",
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
        "java.time.LocalDateTime",
        "java.time.ZoneId",
        "java.time.ZoneOffset",
        "java.io.File"
    );
    
    /**
     * Maximum array size allowed during deserialization to prevent memory exhaustion attacks.
     */
    private static final long MAX_ARRAY_SIZE = 100_000;
    
    /**
     * Maximum object graph depth allowed during deserialization to prevent stack overflow attacks.
     */
    private static final long MAX_DEPTH = 100;
    
    /**
     * Maximum number of object references allowed during deserialization to prevent memory exhaustion.
     */
    private static final long MAX_REFERENCES = 100_000;
    
    /**
     * Checks whether a class is allowed to be deserialized based on the allowlist.
     *
     * @param filterInfo the deserialization context information
     * 
     * @return ALLOWED if the class passes the filter, REJECTED otherwise
     */
    @Override
    public Status checkInput(FilterInfo filterInfo) {
        // Check depth limit
        if (filterInfo.depth() > MAX_DEPTH) {
            return Status.REJECTED;
        }
        
        // Check reference count limit
        if (filterInfo.references() > MAX_REFERENCES) {
            return Status.REJECTED;
        }
        
        // Check array size limit
        if (filterInfo.arrayLength() >= 0 && filterInfo.arrayLength() > MAX_ARRAY_SIZE) {
            return Status.REJECTED;
        }
        
        // Get the class being deserialized
        Class<?> clazz = filterInfo.serialClass();
        if (clazz == null) {
            return Status.UNDECIDED;
        }
        
        String className = clazz.getName();
        
        // Allow primitive types
        if (clazz.isPrimitive()) {
            return Status.ALLOWED;
        }
        
        // Allow enum types
        if (clazz.isEnum()) {
            return Status.ALLOWED;
        }
        
        // Check explicitly allowed classes
        if (ALLOWED_CLASSES.contains(className)) {
            return Status.ALLOWED;
        }
        
        // Check allowed prefixes
        for (String prefix : ALLOWED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return Status.ALLOWED;
            }
        }
        
        // Reject everything else
        return Status.REJECTED;
    }
    
    /**
     * Creates a new instance of the report deserialization filter.
     *
     * @return a new ReportDeserializationFilter instance
     */
    public static ObjectInputFilter createFilter() {
        return new ReportDeserializationFilter();
    }
}
