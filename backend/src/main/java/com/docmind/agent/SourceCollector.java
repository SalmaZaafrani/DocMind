package com.docmind.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Numérote les passages consultés pour que la réponse puisse les citer avec [n]. */
public class SourceCollector {

    private final Map<String, Source> byKey = new LinkedHashMap<>();

    public int add(Long documentId, String documentName, int page, String text) {
        String excerpt = text.replaceAll("(?m)^#{1,6}\\s*", "").replace("**", "").replaceAll("\\s+", " ").trim();
        String key = documentId + ":" + page + ":" + (excerpt.length() > 60 ? excerpt.substring(0, 60) : excerpt);
        Source existing = byKey.get(key);
        if (existing != null) {
            return existing.n();
        }
        int n = byKey.size() + 1;
        if (excerpt.length() > 420) {
            excerpt = excerpt.substring(0, 420).trim() + "…";
        }
        byKey.put(key, new Source(n, documentId, documentName, page, excerpt));
        return n;
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }

    public List<Source> all() {
        return new ArrayList<>(byKey.values());
    }

    /** Convertit les citations au format 【1†L3-L4】 (certains modèles) en [1]. */
    public static String normalizeCitations(String text) {
        return text == null ? null : text.replaceAll("【(\\d+)[^】]*】", "[$1]");
    }

    /** Ne garde que les sources réellement citées dans le texte (toutes si aucune citation). */
    public List<Source> citedIn(String text) {
        List<Source> cited = new ArrayList<>();
        for (Source s : byKey.values()) {
            if (text != null && text.contains("[" + s.n() + "]")) {
                cited.add(s);
            }
        }
        return cited.isEmpty() ? all() : cited;
    }
}
