/*
 * Copyright (C) 2018 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class to merge overlapping integer intervals.
 * <p>
 * This handles sorting and condensing a list of overlapping intervals into a list of contiguous, non-overlapping intervals.
 * </p>
 * 
 * @author Gosu
 */
public final class Ideone {
    /**
     * Internal list storing intervals to be merged.
     */
    private final ArrayList<Interval> list = new ArrayList<>();

    /**
     * Represents an integer interval with a start and an end point.
     */
    public static final class Interval {
        /**
         * The start point of the interval.
         */
        private final int start;

        /**
         * The end point of the interval.
         */
        private final int end;

        /**
         * Constructs a default interval starting and ending at zero.
         */
        Interval() {
            start = 0;
            end = 0;
        }

        /**
         * Constructs an interval with the specified start and end points.
         * 
         * @param s the start of the interval
         * @param e the end of the interval
         */
        Interval(final int s, final int e) {
            start = s;
            end = e;
        }

        /**
         * Returns the start point of this interval.
         * 
         * @return the start coordinate
         */
        public int getStart() {
            return start;
        }

        /**
         * Returns the end point of this interval.
         * 
         * @return the end coordinate
         */
        public int getEnd() {
            return end;
        }
    }

    /** Constructs a new Ideone instance with an empty list of intervals. */
    public Ideone() {
        /* default constructor */
    }

    /**
     * Adds a new interval to the list of intervals to be merged.
     * 
     * @param start the starting index of the interval
     * @param end the ending index of the interval
     */
    public void add(final int start, final int end) {
        list.add(new Interval(start, end));
    }

    /**
     * Merges all intervals added to this instance.
     * 
     * @return a list of merged, non-overlapping intervals
     */
    public List<Interval> merge() {
        return merge(list);
    }

    /**
     * Merges a list of arbitrary intervals into a new list of non-overlapping intervals.
     * 
     * @param intervals the list of intervals to merge
     * 
     * @return a new list of sorted, non-overlapping intervals
     */
    public static List<Interval> merge(final List<Interval> intervals) {
        if (intervals.size() <= 1)
            return intervals;

        intervals.sort(Comparator.comparingInt(Ideone.Interval::getStart));

        final var first = intervals.get(0);
        int start = first.getStart();
        int end = first.getEnd();

        final ArrayList<Interval> result = new ArrayList<>();

        for (var i = 1; i < intervals.size(); i++) {
            final var current = intervals.get(i);
            if (current.getStart() <= end) {
                end = Math.max(current.getEnd(), end);
            } else {
                result.add(new Interval(start, end));
                start = current.getStart();
                end = current.getEnd();
            }
        }

        result.add(new Interval(start, end));
        return result;
    }
}
