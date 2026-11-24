package com.docmind.agent;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.docmind.common.Json;
import com.docmind.llm.GroqClient;
import com.docmind.search.SearchHit;
import com.docmind.search.SearchService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Mode « réponse rapide » : recherche des passages puis une seule réponse du LLM, avec citations. */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 6;

    private final SearchService search;
    private final GroqClient llm;

    public RagService(SearchService search, GroqClient llm) {
        this.search = search;
        this.llm = llm;
    }

    public Answer answer(Long ownerId, List<ObjectNode> history, String question, Set<Long> documentFilter) {
        long start = System.currentTimeMillis();
        List<SearchHit> hits = search.search(ownerId, question, TOP_K, documentFilter);
        SourceCollector sources = new SourceCollector();
        StringBuilder context = new StringBuilder();
        for (SearchHit h : hits) {
            int n = sources.add(h.documentId(), h.documentName(), h.page(), h.text());
            context.append('[').append(n).append("] (").append(h.documentName()).append(", p. ").append(h.page()).append(")\n")
                   .append(h.text()).append("\n\n");
        }
        Step step = new Step("search_documents", "Recherche : « " + question + " »", java.util.Map.of("query", question),
                hits.size() + " passage" + (hits.size() > 1 ? "s" : "") + " trouvé" + (hits.size() > 1 ? "s" : ""),
                System.currentTimeMillis() - start);

        if (hits.isEmpty()) {
            return new Answer("Je n'ai trouvé aucun passage correspondant dans vos documents. Essayez avec d'autres mots-clés, "
                    + "ou vérifiez que le document concerné a bien été importé.", List.of(), List.of(step), modelLabel());
        }
        if (llm.isEnabled()) {
            try {
                ArrayNode messages = Json.MAPPER.createArrayNode();
                messages.add(GroqClient.message("system", Prompts.RAG));
                history.forEach(messages::add);
                messages.add(GroqClient.message("user", "Extraits :\n\n" + context.toString().trim() + "\n\nQuestion : " + question));
                String content = llm.chat(messages, null, 900).content();
                if (content != null && !content.isBlank()) {
                    return new Answer(content.trim(), sources.citedIn(content), List.of(step), llm.model());
                }
            } catch (Exception e) {
                log.warn("LLM indisponible, réponse extractive : {}", e.getMessage());
            }
        }
        return extractive(hits, sources, step);
    }

    /** Réponse sans LLM : les passages les plus pertinents, cités. */
    static Answer extractive(List<SearchHit> hits, SourceCollector sources, Step step) {
        StringBuilder sb = new StringBuilder("Voici les passages les plus pertinents de vos documents :\n\n");
        int count = 0;
        for (SearchHit h : hits) {
            if (count++ == 3) {
                break;
            }
            int n = sources.add(h.documentId(), h.documentName(), h.page(), h.text());
            String text = h.text().replaceAll("(?m)^#{1,6}\\s*", "").replaceAll("\\s+", " ").trim();
            if (text.length() > 320) {
                text = text.substring(0, 320).trim() + "…";
            }
            sb.append("- **").append(h.documentName()).append(", p. ").append(h.page()).append("** : ").append(text)
              .append(" [").append(n).append("]\n");
        }
        sb.append("\n_Réponse sans IA : ajoutez une clé GROQ_API_KEY pour obtenir des réponses rédigées._");
        String content = sb.toString();
        return new Answer(content, sources.citedIn(content), List.of(step), "sans LLM");
    }

    private String modelLabel() {
        return llm.isEnabled() ? llm.model() : "sans LLM";
    }
}
