package com.docmind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docmind.agent.Calculator;
import com.docmind.agent.SourceCollector;
import com.docmind.document.Chunker;
import com.docmind.search.Bm25Index;
import com.docmind.search.SearchHit;
import com.docmind.search.Tokenizer;

/** Tests unitaires de la logique sans base de données ni LLM. */
class CoreLogicTest {

    @Test
    void calculatorHandlesPrioritiesCommasAndErrors() {
        assertEquals(368.0, Calculator.evaluate("18400*0,01*2"), 1e-9);
        assertEquals(-14.0, Calculator.evaluate("-(3+4)*2"), 1e-9);
        assertEquals(512.0, Calculator.evaluate("2^3^2"), 1e-9);
        assertEquals("12.1951", Calculator.format(Calculator.evaluate("(1.84-1.64)/1.64*100")));
        assertThrows(IllegalArgumentException.class, () -> Calculator.evaluate("1/0"));
        assertThrows(IllegalArgumentException.class, () -> Calculator.evaluate("2+abc"));
    }

    @Test
    void chunkerSplitsLongTextWithOverlap() {
        String text = "Phrase numéro un de test. ".repeat(120);
        List<String> chunks = Chunker.split(text);
        assertTrue(chunks.size() > 2);
        chunks.forEach(c -> assertTrue(c.length() <= Chunker.TARGET));
        assertTrue(Chunker.split("   ").isEmpty());
    }

    @Test
    void tokenizerRemovesAccentsAndStopwords() {
        assertEquals(List.of("indemnite", "teletravail"), Tokenizer.tokens("L'indemnité de télétravail"));
    }

    @Test
    void bm25RanksTheRelevantPassageFirst() {
        Bm25Index index = new Bm25Index(List.of(
                new Bm25Index.Entry(1L, 10L, "contrat.pdf", 2, "Pénalité de 1 % du prix annuel par jour de retard."),
                new Bm25Index.Entry(2L, 11L, "teletravail.pdf", 1, "Deux jours de télétravail par semaine."),
                new Bm25Index.Entry(3L, 12L, "rapport.pdf", 4, "Le chiffre d'affaires atteint 1,84 million d'euros.")));
        List<SearchHit> hits = index.search("pénalités de retard", 3, null);
        assertFalse(hits.isEmpty());
        assertEquals(Long.valueOf(1L), hits.get(0).chunkId());
        assertTrue(index.search("pénalités", 3, java.util.Set.of(11L)).isEmpty());
    }

    @Test
    void sourceCollectorNumbersAndDeduplicates() {
        SourceCollector sources = new SourceCollector();
        int a = sources.add(1L, "a.pdf", 1, "## Titre\nTexte A");
        int b = sources.add(2L, "b.pdf", 3, "Texte B");
        assertEquals(1, a);
        assertEquals(2, b);
        assertEquals(1, sources.add(1L, "a.pdf", 1, "## Titre\nTexte A"));
        assertEquals(1, sources.citedIn("Réponse [2].").size());
        assertEquals("Titre Texte A", sources.all().get(0).excerpt());
    }
}
