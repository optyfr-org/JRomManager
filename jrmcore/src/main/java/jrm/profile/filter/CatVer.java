/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.profile.filter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import jrm.profile.Profile;
import jrm.profile.data.PropertyStub;
import lombok.Getter;

/**
 * Manages the parsing, storage, and retrieval of game category assignments from a <code>catver.ini</code> metadata file.
 * <p>
 * This class maps games to categories and subcategories (e.g., "Shooter / Gallery", "Sports / Soccer") and implements
 * {@link jrm.profile.filter.IniProcessor} to parse the configuration file sections.
 * </p>
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public final class CatVer implements Iterable<CatVer.Category>, PropertyStub, IniProcessor {
    /**
     * Represents a game category containing hierarchical subcategories.
     * <p>
     * This class delegates map operations to an internal map of subcategories indexed by their subcategory names.
     * </p>
     * 
     * @author optyfr
     * 
     * @since 1.0
     */
    public final class Category implements Map<String, Category.SubCategory>, Iterable<Category.SubCategory>, PropertyStub {
        /**
         * The name of this category.
         */
        public final String name;

        /**
         * Internal map of subcategories, using the subcategory name as the key.
         */
        private final Map<String, SubCategory> subcategories = new TreeMap<>();

        /**
         * Sorted list of subcategories belonging to this category.
         * 
         * @return the list of subcategories
         */
        private final @Getter List<SubCategory> listSubCategories = new ArrayList<>();

        /**
         * Constructs a new Category instance with the specified name.
         * 
         * @param name the name of the category
         */
        public Category(final String name) {
            this.name = name;
        }

        /**
         * Returns the number of subcategories in this category.
         * 
         * @return the number of subcategories
         */
        @Override
        public int size() {
            return subcategories.size();
        }

        /**
         * Returns {@code true} if this category contains no subcategories.
         * 
         * @return {@code true} if this category is empty, otherwise {@code false}
         */
        @Override
        public boolean isEmpty() {
            return subcategories.isEmpty();
        }

        /**
         * Checks whether a subcategory with the specified key name exists in this category.
         * 
         * @param key the name of the subcategory to look for
         * 
         * @return {@code true} if the subcategory key exists, otherwise {@code false}
         */
        @Override
        public boolean containsKey(final Object key) {
            return subcategories.containsKey(key);
        }

        /**
         * Checks whether the specified subcategory object is mapped in this category.
         * 
         * @param value the subcategory instance to check
         * 
         * @return {@code true} if the subcategory exists, otherwise {@code false}
         */
        @Override
        public boolean containsValue(final Object value) {
            return subcategories.containsValue(value);
        }

        /**
         * Retrieves the subcategory mapped to the specified key name.
         * 
         * @param key the name of the subcategory to get
         * 
         * @return the associated {@link SubCategory} instance, or {@code null} if not found
         */
        @Override
        public SubCategory get(final Object key) {
            return subcategories.get(key);
        }

        /**
         * Mappings a subcategory name to its corresponding {@link SubCategory} instance.
         * 
         * @param key the name of the subcategory
         * @param value the {@link SubCategory} instance to store
         * 
         * @return the previous subcategory mapped to the key, or {@code null} if none
         */
        @Override
        public SubCategory put(final String key, final SubCategory value) {
            return subcategories.put(key, value);
        }

        /**
         * Removes the subcategory mapped to the specified key name.
         * 
         * @param key the name of the subcategory to remove
         * 
         * @return the removed {@link SubCategory} instance, or {@code null} if none existed
         */
        @Override
        public SubCategory remove(final Object key) {
            return subcategories.remove(key);
        }

        /**
         * Copies all of the subcategory mappings from the specified map into this category.
         * 
         * @param m the map containing subcategory mappings to insert
         */
        @Override
        public void putAll(final Map<? extends String, ? extends SubCategory> m) {
            subcategories.putAll(m);
        }

        /**
         * Clears all subcategory mappings from this category.
         */
        @Override
        public void clear() {
            subcategories.clear();
        }

        /**
         * Returns a set of all subcategory names registered in this category.
         * 
         * @return a {@link Set} of subcategory keys
         */
        @Override
        public Set<String> keySet() {
            return subcategories.keySet();
        }

        /**
         * Returns a collection of all subcategory instances in this category.
         * 
         * @return a {@link Collection} of {@link SubCategory} elements
         */
        @Override
        public Collection<SubCategory> values() {
            return subcategories.values();
        }

        /**
         * Returns a set of the key-value entry mappings contained in this category.
         * 
         * @return a {@link Set} of map entries
         */
        @Override
        public Set<Entry<String, SubCategory>> entrySet() {
            return subcategories.entrySet();
        }

        /**
         * Generates a descriptive user-friendly object representation of the category, showing its name and the sum of selected
         * games within its subcategories.
         * 
         * @return a descriptive string for display in user interfaces
         */
        public Object getUserObject() {
            return String.format("%s (%d)", name, listSubCategories.stream().filter(SubCategory::isSelected).mapToInt(SubCategory::size).sum()); //$NON-NLS-1$
        }

        /**
         * Returns a string representation of this category.
         * 
         * @return a string combining the category name and its active game count
         */
        @Override
        public String toString() {
            return (String) getUserObject();
        }

        /**
         * Returns an iterator over the subcategories registered in this category.
         * 
         * @return an {@link Iterator} of {@link SubCategory} elements
         */
        @Override
        public Iterator<SubCategory> iterator() {
            return listSubCategories.iterator();
        }

        /**
         * Resolves the configuration property key associated with this category filter.
         * 
         * @return the fully qualified configuration property key string
         */
        @Override
        public String getPropertyName() {
            return "filter.cat." + name; //$NON-NLS-1$
        }

        /**
         * Sets the selection state of this category in the profile settings.
         * 
         * @param selected {@code true} to enable this category filter, or {@code false} to disable it
         */
        public void setSelected(final boolean selected) {
            PropertyStub.super.setSelected(profile, selected);
        }

        /**
         * Checks whether this category is currently selected in the active profile.
         * 
         * @return {@code true} if this category filter is enabled, otherwise {@code false}
         */
        public boolean isSelected() {
            return PropertyStub.super.isSelected(profile);
        }

        /**
         * Represents a game subcategory containing a collection of mapped games.
         * 
         * @author optyfr
         * 
         * @since 1.0
         */
        public final class SubCategory extends GamesList implements PropertyStub {
            /**
             * The name of this subcategory.
             */
            public final String name;

            /**
             * Constructs a new SubCategory instance.
             * 
             * @param name the name of this subcategory
             */
            public SubCategory(final String name) {
                this.name = name;
            }

            /**
             * Generates a user-friendly descriptive representation of this subcategory, showing its name and total number of games.
             * 
             * @return a descriptive string for UI rendering
             */
            public Object getUserObject() {
                return name + " (" + games.size() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
            }

            /**
             * Returns a string representation of this subcategory.
             * 
             * @return a string combining the subcategory name and mapped games count
             */
            @Override
            public String toString() {
                return (String) getUserObject();
            }

            /**
             * Resolves the configuration property key associated with this subcategory filter.
             * 
             * @return the fully qualified configuration property key string
             */
            @Override
            public String getPropertyName() {
                return "filter.cat." + Category.this.name + "." + name; //$NON-NLS-1$ //$NON-NLS-2$
            }

            /**
             * Sets the selection state of this subcategory in the profile settings.
             * 
             * @param selected {@code true} to enable this subcategory filter, or {@code false} to disable it
             */
            public void setSelected(final boolean selected) {
                PropertyStub.super.setSelected(profile, selected);
            }

            /**
             * Checks whether this subcategory is currently selected in the active profile.
             * 
             * @return {@code true} if this subcategory filter is enabled, otherwise {@code false}
             */
            public boolean isSelected() {
                return PropertyStub.super.isSelected(profile);
            }

            /**
             * Retrieves the parent Category instance that encloses this subcategory.
             * 
             * @return the parent {@link Category} instance
             */
            public Category getParent() {
                return Category.this;
            }
        }
    }

    /**
     * The profile context associated with this categories filter.
     */
    private final Profile profile;

    /**
     * The list of parsed game categories.
     * 
     * @return the list containing all initialized {@link Category} instances
     */
    private final @Getter List<Category> listCategories = new ArrayList<>();

    /**
     * The source configuration {@link File} from which categories are read.
     */
    public final File file;

    /**
     * Main constructor, which reads the provided catver.ini and initializes the list of categories, subcategories, and mapped game
     * codes.
     * 
     * @param profile the active {@link Profile} context
     * @param file the catver.ini configuration {@link File} to parse
     * 
     * @throws IOException if a file reading error occurs, or if no valid categories are extracted
     */
    private CatVer(final Profile profile, final File file) throws IOException {
        final Map<String, Category> categories = new TreeMap<>();
        this.profile = profile;
        this.file = file;
        processFile(file, kv -> {
            final String[] v = StringUtils.split(kv[1], '/');
            if (v.length == 2) {
                final var cat = categories.computeIfAbsent(v[0].trim(), Category::new);
                final var subcat = cat.computeIfAbsent(v[1].trim(), s -> cat.new SubCategory(s)); // NOSONAR
                subcat.add(kv[0].trim());
            }
        });
        listCategories.addAll(categories.values());
        for (final Category cat : listCategories)
            cat.listSubCategories.addAll(cat.subcategories.values());
        if (listCategories.isEmpty())
            throw new IOException(profile.getSession().getMsgs().getString("CatVer.NoCatVerData")); //$NON-NLS-1$
    }

    /**
     * Returns the specific section of the INI configuration file targeted for parsing.
     * 
     * @return the section header {@code "[Category]"}
     */
    @Override
    public String getSection() {
        return "[Category]";
    }

    /**
     * Factory method to read and instantiate a new {@code CatVer} categories filter registry for the specified profile.
     * 
     * @param profile the active {@link Profile} context
     * @param file the target {@code catver.ini} file to parse
     * 
     * @return an initialized {@link CatVer} manager containing mapped game categories
     * 
     * @throws IOException if a file reading error occurs, or if parsing fails to find valid entries
     */
    public static CatVer read(final Profile profile, final File file) throws IOException {
        return new CatVer(profile, file);
    }

    /**
     * Generates a descriptive user-friendly representation of all categories, showing a general label and the grand total of all
     * selected games.
     * 
     * @return a descriptive string for UI representation
     */
    public Object getUserObject() {
        return String.format("%s (%d)", profile.getSession().getMsgs().getString("CatVer.AllCategories"), //$NON-NLS-1$ //$NON-NLS-2$
                listCategories.stream().flatMap(c -> c.listSubCategories.stream().filter(Category.SubCategory::isSelected)).mapToInt(Category.SubCategory::size).sum());
    }

    /**
     * Returns a string representation of this category manager.
     * 
     * @return a string combining the total categories label and selected game sum
     */
    @Override
    public String toString() {
        return (String) getUserObject();
    }

    /**
     * Returns an iterator over all parsed game categories.
     * 
     * @return an {@link Iterator} of {@link Category} elements
     */
    @Override
    public Iterator<Category> iterator() {
        return listCategories.iterator();
    }

    /**
     * Resolves the configuration property key associated with the global categories filter.
     * 
     * @return the fully qualified configuration property key string
     */
    @Override
    public String getPropertyName() {
        return "filter.cat"; //$NON-NLS-1$
    }

    /**
     * Sets the selection state of the global categories filter in the profile settings.
     * 
     * @param selected {@code true} to enable categories filtering, or {@code false} to disable it
     */
    public void setSelected(final boolean selected) {
        PropertyStub.super.setSelected(profile, selected);
    }

    /**
     * Checks whether the global categories filter is currently enabled in the profile.
     * 
     * @return {@code true} if categories filtering is enabled, otherwise {@code false}
     */
    public boolean isSelected() {
        return PropertyStub.super.isSelected(profile);
    }

}
