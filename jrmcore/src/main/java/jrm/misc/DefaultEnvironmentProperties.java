package jrm.misc;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles application configuration properties loaded from input streams, environment variables, or system properties, supporting
 * placeholder resolution.
 * <p>
 * Placeholders are specified using {@code ${property.name}} or {@code $property.name} and are resolved recursively from the loaded
 * properties or environment map.
 * </p>
 * 
 * @author optyfr
 */
public class DefaultEnvironmentProperties {
    /**
     * Map containing resolved property keys and values.
     */
    private final Map<String, String> map;

    /**
     * Regular expression pattern for detecting placeholders in property values, such as {@code $$}, {@code ${...}}, or
     * {@code $...}.
     */
    private static final Pattern REPLACEMENT_PATTERN = Pattern.compile("([$][$])|([$]\\{.*\\})|([$]\\w+)");

    /**
     * Maximum nested placeholder expansion depth.
     */
    static final int MAX_REPLACEMENT_DEPTH = 32;

    /**
     * Constructs a new {@code DefaultEnvironmentProperties} instance by reading from the specified {@link InputStream}.
     * 
     * @param is the input stream containing properties, can be null or fail-safe
     */
    public DefaultEnvironmentProperties(InputStream is) {
        Properties properties = new Properties();
        try {
            properties.load(is);
        } catch (IOException | NullPointerException _) {
            properties.clear();
        }
        map = init(properties);
    }

    /**
     * Constructs a new {@code DefaultEnvironmentProperties} instance wrapping pre-loaded {@link Properties}.
     * 
     * @param properties the properties object containing initial key-value mappings
     */
    public DefaultEnvironmentProperties(Properties properties) {
        map = init(properties);
    }

    /**
     * Factory method to obtain an instance based on a properties resource located in the same package as the specified loader
     * class, with the class name as the filename base.
     * 
     * @param loader the class whose class loader and package are used to find the properties file
     * 
     * @return a configured {@code DefaultEnvironmentProperties} instance
     */
    public static DefaultEnvironmentProperties getInstance(Class<?> loader) {
        try (final var is = loader.getResourceAsStream(loader.getSimpleName() + ".properties")) {
            return new DefaultEnvironmentProperties(is);
        } catch (IOException _) {
            return new DefaultEnvironmentProperties(new Properties());
        }
    }

    /**
     * Initializes the internal property map by copying, adding environment variables, and resolving placeholders.
     * 
     * @param properties the initial raw properties
     * 
     * @return an unmodifiable map containing resolved values
     */
    private Map<String, String> init(Properties properties) {
        var tempMap = properties.entrySet().stream().collect(Collectors.toUnmodifiableMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
        return resolvePlaceHolders(addEnvironment(tempMap));
    }

    /**
     * Retrieves a raw environment/property configuration value by key.
     * 
     * @param key the property key to search for
     * 
     * @return an {@link Optional} containing the value if present, or empty otherwise
     */
    private Optional<String> getEnvironmentProperties(String key) {
        return Optional.ofNullable(map.get(key));
    }

    /**
     * Retrieves a string property value. If not found in the local configuration, system properties are queried before returning
     * the default value.
     * 
     * @param key the property key
     * @param def the default value to return if the key is not defined
     * 
     * @return the resolved string value, or the default value
     */
    public String getProperty(String key, String def) {
        return getEnvironmentProperties(key).orElseGet(() -> System.getProperty(key, def));
    }

    /**
     * Retrieves an integer property value. If not found or if a number format error occurs, system properties are checked, or the
     * default value is returned.
     * 
     * @param key the property key
     * @param def the default value to return on absence or error
     * 
     * @return the resolved integer value, or the default value
     */
    public Integer getProperty(String key, Integer def) {
        return getEnvironmentProperties(key).map(v -> {
            try {
                return Integer.valueOf(v);
            } catch (NumberFormatException _) {
                return null;
            }
        }).orElseGet(() -> {
            try {
                return Integer.valueOf(System.getProperty(key, def.toString()));
            } catch (NumberFormatException _) {
                return def;
            }
        });
    }

    /**
     * Retrieves a boolean property value. If not found, system properties are queried, or the default value is returned.
     * 
     * @param key the property key
     * @param def the default value to return on absence or error
     * 
     * @return the resolved boolean value, or the default value
     */
    public Boolean getProperty(String key, Boolean def) {
        return getEnvironmentProperties(key).flatMap(DefaultEnvironmentProperties::parseBoolean)
                .or(() -> parseBoolean(System.getProperty(key, def.toString()))).orElse(def);
    }

    private static Optional<Boolean> parseBoolean(String value) {
        if (value.equalsIgnoreCase("true")) //$NON-NLS-1$
            return Optional.of(Boolean.TRUE);
        if (value.equalsIgnoreCase("false")) //$NON-NLS-1$
            return Optional.of(Boolean.FALSE);
        return Optional.empty();
    }

    /**
     * Retrieves an enum property value. Resolves the value to the matching constant of the specified enum class.
     * 
     * @param <T> the type of the enum
     * @param cls the class of the enum type
     * @param key the property key
     * @param def the default enum value to return on absence or failure
     * 
     * @return the resolved enum constant, or the default value
     */
    public <T extends Enum<T>> T getProperty(Class<T> cls, String key, T def) {
        return getEnvironmentProperties(key).map(v -> {
            try {
                return Enum.valueOf(cls, v);
            } catch (Exception _) {
                return null;
            }
        }).orElseGet(() -> {
            try {
                return Enum.valueOf(cls, System.getProperty(key, def.toString()));
            } catch (Exception _) {
                return def;
            }
        });
    }

    /**
     * Retrieves a string property value without a default fallback.
     * 
     * @param key the property key
     * 
     * @return the property value, or the matching system property if not defined in the local map
     */
    public String getProperty(String key) {
        return getEnvironmentProperties(key).orElseGet(() -> System.getProperty(key));
    }

    /**
     * Resolves all placeholders recursively within the provided map. Keys starting with {@code JRM_} have their values parsed for
     * replacement sequences.
     * 
     * @param mapin the input map of key-value pairs containing potential placeholders
     * 
     * @return an unmodifiable map with fully resolved placeholder values
     */
    private static Map<String, String> resolvePlaceHolders(final Map<String, String> mapin) {
        final Function<? super Map.Entry<String, String>, String> mapper = e -> e.getKey().startsWith("JRM_") ? replaceValue(e.getValue(), mapin) : e.getValue();
        return mapin.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, mapper));
    }

    /**
     * Replaces placeholders recursively within a single string value using the provided reference map. Supports {@code $$},
     * standard environment variable style, and custom braces syntax.
     * 
     * @param value the raw value containing placeholders
     * @param mapin the source map used for lookup
     * 
     * @return the resolved string value
     */
    private static String replaceValue(final String value, final Map<String, String> mapin) {
        return replaceValue(value, mapin, 0);
    }

    private static String replaceValue(final String value, final Map<String, String> mapin, final int depth) {
        if (value == null || depth >= MAX_REPLACEMENT_DEPTH)
            return value;
        return REPLACEMENT_PATTERN.matcher(value).replaceAll(mr -> {
            String ret = null;
            String item = mr.group();
            if ("$$".equals(item))
                return "\\$";
            if (item.startsWith("${") && item.endsWith("}"))
                ret = mapin.get(item.substring(2, item.length() - 1));
            else if (item.length() > 1)
                ret = mapin.get(item.substring(1));
            if (ret == null)
                return Matcher.quoteReplacement(mr.group());
            return Matcher.quoteReplacement(replaceValue(ret, mapin, depth + 1));
        });
    }

    /**
     * Merges system environment variables into the provided properties map. Converts keys to lower-case and replaces underscores
     * with dots.
     * 
     * @param mapin the source properties map
     * 
     * @return a combined unmodifiable map of properties and environment values
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> addEnvironment(final Map<String, String> mapin) {
        final var env = new HashMap<String, String>(System.getenv());
        final var temp = new HashMap<String, String>(mapin);
        resolvePlaceHolders(env).entrySet().forEach(e -> temp.put(e.getKey().toLowerCase().replace('_', '.'), e.getValue()));
        return Map.ofEntries(temp.entrySet().toArray(new Map.Entry[temp.entrySet().size()]));
    }

}
