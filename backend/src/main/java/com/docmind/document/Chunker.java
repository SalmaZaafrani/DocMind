package com.docmind.document;

import java.util.ArrayList;
import java.util.List;

/** Découpe le texte d'une page en passages qui se chevauchent légèrement. */
public final class Chunker {

    public static final int TARGET = 900;
    public static final int OVERLAP = 150;

    private Chunker() {
    }

    public static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String t = text.trim();
        int start = 0;
        while (start < t.length()) {
            int end = Math.min(start + TARGET, t.length());
            if (end < t.length()) {
                end = bestBreak(t, start + TARGET / 2, end);
            }
            chunks.add(t.substring(start, end).trim());
            if (end >= t.length()) {
                break;
            }
            int next = end - OVERLAP;
            // Reprendre au début d'un mot
            while (next > start && next < t.length() && !Character.isWhitespace(t.charAt(next - 1))) {
                next--;
            }
            start = next <= start ? end : next;
        }
        return chunks;
    }

    /** Fin de phrase ou de paragraphe la plus proche de la limite, sinon espace. */
    private static int bestBreak(String t, int min, int max) {
        for (int i = max; i > min; i--) {
            char c = t.charAt(i - 1);
            if (c == '\n' || ((c == '.' || c == '!' || c == '?' || c == ';') && Character.isWhitespace(t.charAt(i)))) {
                return i;
            }
        }
        for (int i = max; i > min; i--) {
            if (Character.isWhitespace(t.charAt(i - 1))) {
                return i;
            }
        }
        return max;
    }
}
