package com.docmind.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Découpe un texte en termes normalisés (minuscules, sans accents, sans mots vides). */
public final class Tokenizer {

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
        // français
        "a", "au", "aux", "avec", "ce", "ces", "cet", "cette", "dans", "de", "des", "du", "elle", "en", "est", "et",
        "etre", "eux", "il", "ils", "je", "la", "le", "les", "leur", "leurs", "lui", "ma", "mais", "me", "meme", "mes",
        "moi", "mon", "ne", "nos", "notre", "nous", "on", "ou", "par", "pas", "pour", "qu", "que", "qui", "sa", "se",
        "ses", "son", "sont", "sur", "ta", "te", "tes", "toi", "ton", "tu", "un", "une", "vos", "votre", "vous", "y",
        "quel", "quelle", "quels", "quelles", "quoi", "comment", "combien", "est-ce", "ete", "fait", "faire", "peut",
        "plus", "tout", "tous", "toute", "toutes", "sans", "si", "lors", "entre", "selon", "ainsi", "donc", "cela",
        // anglais
        "the", "of", "and", "to", "in", "is", "it", "that", "for", "on", "with", "as", "are", "was", "be", "by", "an",
        "this", "what", "which", "how", "or", "from", "at", "do", "does", "can", "about", "my", "our", "your"));

    private Tokenizer() {
    }

    public static String normalize(String text) {
        String n = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : normalize(text).split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    /** Racinisation très légère : pluriels et quelques terminaisons courantes. */
    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ements")) return w.substring(0, w.length() - 1);
        if (w.length() > 4 && (w.endsWith("s") || w.endsWith("x")) && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }
}
