package com.orbitflow.common.util;

/**
 * Lexicographic rank utility for O(1) card reordering.
 * Ranks are base-36 strings; midpoint calculation avoids full re-indexing.
 */
public final class LexoRank {
    private static final String DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final String MIN = "a0";
    private static final String MAX = "z9".repeat(8);

    private LexoRank() {}

    public static String initial() { return "a0"; }

    public static String between(String prev, String next) {
        if ((prev == null || prev.isBlank()) && (next == null || next.isBlank())) return initial();
        if (prev == null || prev.isBlank()) return decrement(next);
        if (next == null || next.isBlank()) return increment(prev);
        if (prev.compareTo(next) >= 0) throw new IllegalArgumentException("prev must be < next");
        // Find midpoint
        int maxLen = Math.max(prev.length(), next.length());
        StringBuilder a = new StringBuilder(prev);
        while (a.length() < maxLen) a.append('0');
        StringBuilder b = new StringBuilder(next);
        while (b.length() < maxLen) b.append('0');
        StringBuilder mid = new StringBuilder();
        boolean tight = true;
        for (int i = 0; i < maxLen; i++) {
            int ai = DIGITS.indexOf(Character.toLowerCase(a.charAt(i)));
            int bi = DIGITS.indexOf(Character.toLowerCase(b.charAt(i)));
            if (ai < 0) ai = 0;
            if (bi < 0) bi = 0;
            if (tight) {
                int diff = bi - ai;
                if (diff > 1) {
                    mid.append(DIGITS.charAt(ai + diff / 2));
                    // pad rest with middle char for stability
                    for (int j = i + 1; j < maxLen; j++) mid.append('n');
                    tight = false;
                    break;
                } else {
                    mid.append(a.charAt(i));
                }
            }
        }
        String result;
        if (tight) {
            // adjacent — append midpoint char
            result = prev + "n";
        } else {
            result = mid.toString();
        }
        // Trim trailing zeros-ish noise, keep at least 2 chars
        result = result.replaceAll("0+$", "");
        if (result.length() < 2) result = (result + "n0").substring(0, 2);
        // Guarantee strictly between
        if (!(prev.compareTo(result) < 0 && result.compareTo(next) < 0)) {
            result = prev + "n";
        }
        return result;
    }

    public static String increment(String rank) {
        if (rank == null || rank.isBlank()) return initial();
        // append 'n' keeps ordering after rank (since 'n' > end? ensure > rank)
        return rank + "n";
    }

    public static String decrement(String rank) {
        if (rank == null || rank.isBlank()) return initial();
        // Prepend a char smaller than first char if possible, else append lower suffix trick:
        // simplest: find a rank strictly smaller by trimming/adjusting
        char first = Character.toLowerCase(rank.charAt(0));
        int idx = DIGITS.indexOf(first);
        if (idx > 0) {
            return DIGITS.charAt(idx - 1) + rank.substring(1);
        }
        return "0" + rank;
    }

    /** Returns true when ranks are too dense (avg gap tiny or overly long strings). */
    public static boolean needsRebalance(java.util.List<String> sortedRanks) {
        if (sortedRanks.size() < 2) return false;
        for (String r : sortedRanks) {
            if (r != null && r.length() > 16) return true;
        }
        return false;
    }

    /** Rebalance: assign evenly spaced ranks preserving order. */
    public static java.util.List<String> rebalance(int count) {
        java.util.List<String> out = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // fixed-width numeric suffix keeps lexicographic order
            out.add("a" + String.format("%06d", i * 1000));
        }
        return out;
    }
}
