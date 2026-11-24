package com.docmind.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Index BM25 en mémoire : le classement classique des moteurs de recherche,
 * qui favorise les passages contenant les termes rares de la question.
 */
public class Bm25Index {

    public record Entry(Long chunkId, Long documentId, String documentName, int page, String text) { }

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final List<Entry> entries = new ArrayList<>();
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final List<Integer> lengths = new ArrayList<>();
    private final Map<String, Integer> docFreq = new HashMap<>();
    private double avgLength;

    public Bm25Index(Collection<Entry> items) {
        long total = 0;
        for (Entry e : items) {
            // Le nom du document est indexé avec le passage
            List<String> toks = Tokenizer.tokens(e.documentName() + " " + e.text());
            Map<String, Integer> tf = new HashMap<>();
            for (String t : toks) {
                tf.merge(t, 1, Integer::sum);
            }
            for (String t : tf.keySet()) {
                docFreq.merge(t, 1, Integer::sum);
            }
            entries.add(e);
            termFreqs.add(tf);
            lengths.add(toks.size());
            total += toks.size();
        }
        avgLength = entries.isEmpty() ? 1 : Math.max(1.0, (double) total / entries.size());
    }

    public int size() {
        return entries.size();
    }

    public List<SearchHit> search(String query, int topK, Set<Long> onlyDocuments) {
        Set<String> terms = new HashSet<>(Tokenizer.tokens(query));
        List<SearchHit> hits = new ArrayList<>();
        if (terms.isEmpty()) {
            return hits;
        }
        int n = entries.size();
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            if (onlyDocuments != null && !onlyDocuments.isEmpty() && !onlyDocuments.contains(e.documentId())) {
                continue;
            }
            Map<String, Integer> tf = termFreqs.get(i);
            double score = 0;
            for (String term : terms) {
                Integer f = tf.get(term);
                if (f == null) {
                    continue;
                }
                int df = docFreq.getOrDefault(term, 0);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                double norm = f * (K1 + 1) / (f + K1 * (1 - B + B * lengths.get(i) / avgLength));
                score += idf * norm;
            }
            if (score > 0) {
                hits.add(new SearchHit(e.chunkId(), e.documentId(), e.documentName(), e.page(), e.text(), score));
            }
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.size() > topK ? new ArrayList<>(hits.subList(0, topK)) : hits;
    }
}
