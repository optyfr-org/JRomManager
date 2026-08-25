package jrm.profile.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareList;

/**
 * An abstract base class for analyzing game descriptions, extracting bracketed keywords, and performing tag-based and keyword-based
 * filtering on items in an {@link AnywareList}.
 * <p>
 * This class parses parentheses and brackets in game descriptions to discover tags (e.g., "M92", "En,Fr,De", "Bootleg") and
 * supports filtering game lists based on user selections.
 * </p>
 * 
 * @author optyfr
 * 
 * @since 1.0
 */
public abstract class Keywords {

    /**
     * Constructs a new Keywords instance with default settings. This constructor does not perform any specific initialization and
     * can be used to create an instance of the Keywords class for analyzing game descriptions and filtering lists based on
     * extracted keywords.
     */
    protected Keywords() {
        /* default constructor */ }

    /**
     * Regex pattern to match the base name and subsequent parentheses-enclosed tags in descriptions.
     */
    final Pattern pattern = Pattern.compile("^(.*?)(\\(.*\\))++"); //$NON-NLS-1$

    /**
     * Regex pattern to capture the text contents inside a single set of parentheses.
     */
    final Pattern patternParenthesis = Pattern.compile("\\((.*?)\\)"); //$NON-NLS-1$

    /**
     * Regex pattern to split tags within parentheses by commas.
     */
    final Pattern patternSplit = Pattern.compile(","); //$NON-NLS-1$

    /**
     * Regex pattern to restrict keywords to alphabetical characters only.
     */
    final Pattern patternAlpha = Pattern.compile("^[a-zA-Z]*$"); //$NON-NLS-1$

    /**
     * Temporary set used to aggregate extracted keywords during analysis.
     */
    final HashSet<String> keywordSet = new HashSet<>();

    /**
     * Functional callback interface triggered after keyword selections are confirmed.
     */
    public interface KFCallBack {
        /**
         * Callback method containing the list to update and the active filters.
         *
         * @param list the targeted {@link AnywareList} being filtered
         * @param filter the list of confirmed keyword tags to keep
         */
        public void call(AnywareList<? extends Anyware> list, List<String> filter);
    }

    /**
     * Internal class representing keyword matching priority preferences for grouping matched ROMs and retaining those of the
     * highest-ranked filter order.
     */
    private class KeyPref {
        /**
         * The matching priority index from the active filter list (lower values indicate higher preference).
         */
        int order;

        /**
         * The list of associated matched {@link Anyware} game entries.
         */
        List<Anyware> wares = new ArrayList<>();

        /**
         * Constructs a new {@code KeyPref} instance with the specified order and an initial game entry.
         *
         * @param order the priority order index
         * @param ware the initial {@link Anyware} entry
         */
        private KeyPref(int order, Anyware ware) {
            this.order = order;
            add(ware);
        }

        /**
         * Associates an additional matched game entry with this priority tier and marks it as selected.
         *
         * @param ware the matched {@link Anyware} entry to add
         */
        private void add(Anyware ware) {
            ware.setSelected(true);
            this.wares.add(ware);
        }

        /**
         * Unselects all accumulated game entries and clears the internal reference list.
         */
        private void clear() {
            wares.forEach(w -> w.setSelected(false));
            wares.clear();
        }
    }

    /**
     * Scans description metadata across the provided list to extract all alphabetical keywords, then prompts the user via the
     * implementation of {@link #showFilter(String[], KFCallBack)}.
     * 
     * @param list the {@link AnywareList} to extract keywords from and filter
     */
    public void filter(AnywareList<? extends Anyware> list) {
        list.getFilteredStream().forEach(ware -> {
            final var matcher = pattern.matcher(ware.getDescription());
            if (matcher.find() && matcher.groupCount() > 1 && matcher.group(2) != null) {
                final var matcherParenthesis = patternParenthesis.matcher(matcher.group(2));
                while (matcherParenthesis.find()) {
                    Arrays.asList(patternSplit.split(matcherParenthesis.group(1))).stream().map(s -> s.trim().toLowerCase()).filter(patternAlpha.asPredicate())
                            .forEach(keywordSet::add);
                }
            }
        });
        showFilter(keywordSet.stream().sorted(String::compareToIgnoreCase).toArray(String[]::new), this::filterCallBack);
    }

    /**
     * Abstract method implemented by platform UI modules to render the keywords filtering checklist dialog to the user.
     * 
     * @param keywords the sorted unique alphabetical tags discovered in descriptions
     * @param callback the {@link KFCallBack} instance to invoke with the user's selections
     */
    protected abstract void showFilter(String[] keywords, KFCallBack callback);

    /**
     * Abstract callback method implemented by UI views to request components reload and refresh lists to present newly applied
     * selection filters.
     */
    protected abstract void updateList();

    /**
     * Receives selected keywords from the dialog, analyzes the description of each game, and determines which entities remain
     * selected using a preference map hierarchy.
     * 
     * @param list the targeted {@link AnywareList} being filtered
     * @param filter the confirmed list of keywords selected by the user
     */
    public void filterCallBack(AnywareList<? extends Anyware> list, List<String> filter) {
        HashMap<String, KeyPref> prefmap = new HashMap<>();
        list.getFilteredStream().forEach(ware -> {
            final var matcher = pattern.matcher(ware.getDescription());
            keywordSet.clear();
            if (matcher.find()) {
                if (matcher.groupCount() > 1 && matcher.group(2) != null) {
                    final var matcherParenthesis = patternParenthesis.matcher(matcher.group(2));
                    while (matcherParenthesis.find()) {
                    Arrays.stream(patternSplit.split(matcherParenthesis.group(1))).map(s -> s.trim().toLowerCase()).filter(patternAlpha.asPredicate())
                                .forEach(keywordSet::add);
                    }
                }
                selectFromKeywords(filter, prefmap, ware, matcher);
            }
        });

        updateList();
    }

    /**
     * Performs fine-grained priority selections on matched games according to user keyword priorities.
     *
     * @param filter the confirmed list of keywords selected by the user
     * @param prefmap the active preference-ranking storage map
     * @param ware the current {@link Anyware} game entry being analyzed
     * @param matcher the regular expression matcher mapping details for the game
     */
    private void selectFromKeywords(final List<String> filter, HashMap<String, KeyPref> prefmap, Anyware ware, final Matcher matcher) {
        for (var i = 0; i < filter.size(); i++) {
            if (keywordSet.contains(filter.get(i))) {
                final var pos = i;
                prefmap.compute(matcher.group(1), (_, pref) -> {
                    if (pref == null)
                        return new KeyPref(pos, ware);
                    else if (pos < pref.order) {
                        pref.clear();
                        return new KeyPref(pos, ware);
                    } else if (pos == pref.order)
                        pref.add(ware);
                    return pref;
                });
                break;
            }
        }
    }

}
