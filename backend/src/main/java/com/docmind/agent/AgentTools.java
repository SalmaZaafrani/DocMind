package com.docmind.agent;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.docmind.common.Json;
import com.docmind.document.Document;
import com.docmind.document.DocumentPage;
import com.docmind.document.DocumentPageRepository;
import com.docmind.document.DocumentRepository;
import com.docmind.note.Note;
import com.docmind.note.NoteRepository;
import com.docmind.search.SearchHit;
import com.docmind.search.SearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/** Outils mis à disposition de l'agent, au format "function calling" d'OpenAI / Groq. */
@Component
public class AgentTools {

    /** Contexte d'une exécution : utilisateur, conversation, documents ciblés et sources collectées. */
    public record Context(Long ownerId, Long conversationId, Set<Long> documentFilter, SourceCollector sources) { }

    /** Résultat d'un outil : texte renvoyé au modèle et libellé lisible pour l'interface. */
    public record Result(String forModel, String label, String summary) { }

    private static final String DEFINITIONS = """
        [
          {"type": "function", "function": {
            "name": "search_documents",
            "description": "Recherche les passages les plus pertinents dans les documents de l'utilisateur. Chaque passage est numéroté [n] pour être cité.",
            "parameters": {"type": "object", "properties": {
              "query": {"type": "string", "description": "Mots-clés précis à rechercher"},
              "document_ids": {"type": "array", "items": {"type": "integer"}, "description": "Limiter la recherche à ces documents (facultatif)"},
              "top_k": {"type": "integer", "description": "Nombre de passages, 5 par défaut, 8 maximum"}
            }, "required": ["query"]}
          }},
          {"type": "function", "function": {
            "name": "list_documents",
            "description": "Liste les documents disponibles avec leur identifiant, leur nombre de pages et un résumé.",
            "parameters": {"type": "object", "properties": {}}
          }},
          {"type": "function", "function": {
            "name": "read_document",
            "description": "Lit le texte complet de pages d'un document (4 pages maximum par appel). Utile pour résumer ou comparer.",
            "parameters": {"type": "object", "properties": {
              "document_id": {"type": "integer"},
              "page_from": {"type": "integer", "description": "Première page, 1 par défaut"},
              "page_to": {"type": "integer", "description": "Dernière page"}
            }, "required": ["document_id"]}
          }},
          {"type": "function", "function": {
            "name": "calculate",
            "description": "Calcule une expression arithmétique exacte, par exemple 18400*2*0.01 ou (1840-1642)/1642*100.",
            "parameters": {"type": "object", "properties": {
              "expression": {"type": "string"}
            }, "required": ["expression"]}
          }},
          {"type": "function", "function": {
            "name": "create_note",
            "description": "Enregistre une note pour l'utilisateur (synthèse, liste d'actions, comparaison). À utiliser seulement s'il le demande.",
            "parameters": {"type": "object", "properties": {
              "title": {"type": "string"},
              "content": {"type": "string", "description": "Contenu en markdown simple"}
            }, "required": ["title", "content"]}
          }}
        ]
        """;

    private static final int READ_MAX_PAGES = 4;
    private static final int READ_MAX_CHARS = 7000;

    private final SearchService search;
    private final DocumentRepository documents;
    private final DocumentPageRepository pages;
    private final NoteRepository notes;
    private final ArrayNode definitions;

    public AgentTools(SearchService search, DocumentRepository documents, DocumentPageRepository pages, NoteRepository notes) {
        this.search = search;
        this.documents = documents;
        this.pages = pages;
        this.notes = notes;
        try {
            this.definitions = (ArrayNode) Json.MAPPER.readTree(DEFINITIONS);
        } catch (Exception e) {
            throw new IllegalStateException("Définition des outils invalide", e);
        }
    }

    public ArrayNode definitions() {
        return definitions.deepCopy();
    }

    public Result execute(String name, JsonNode args, Context ctx) {
        return switch (name) {
            case "search_documents" -> searchDocuments(args, ctx);
            case "list_documents" -> listDocuments(ctx);
            case "read_document" -> readDocument(args, ctx);
            case "calculate" -> calculate(args);
            case "create_note" -> createNote(args, ctx);
            default -> new Result("Outil inconnu : " + name, "Outil inconnu", "Erreur");
        };
    }

    private Result searchDocuments(JsonNode args, Context ctx) {
        String query = args.path("query").asText("").trim();
        if (query.isEmpty()) {
            return new Result("Le paramètre query est obligatoire.", "Recherche", "Requête vide");
        }
        int topK = Math.max(1, Math.min(8, args.path("top_k").asInt(5)));
        Set<Long> filter = new HashSet<>(ctx.documentFilter() == null ? Set.of() : ctx.documentFilter());
        if (args.path("document_ids").isArray()) {
            for (JsonNode id : args.path("document_ids")) {
                filter.add(id.asLong());
            }
        }
        List<SearchHit> hits = search.search(ctx.ownerId(), query, topK, filter);
        String label = "Recherche : « " + query + " »";
        if (hits.isEmpty()) {
            return new Result("Aucun passage trouvé pour : " + query + ". Essaie d'autres mots-clés.", label, "Aucun passage trouvé");
        }
        StringBuilder sb = new StringBuilder();
        for (SearchHit h : hits) {
            int n = ctx.sources().add(h.documentId(), h.documentName(), h.page(), h.text());
            sb.append('[').append(n).append("] (").append(h.documentName()).append(", p. ").append(h.page())
              .append(", document_id=").append(h.documentId()).append(")\n").append(h.text()).append("\n\n");
        }
        return new Result(sb.toString().trim(), label, hits.size() + " passage" + (hits.size() > 1 ? "s" : "") + " trouvé" + (hits.size() > 1 ? "s" : ""));
    }

    private Result listDocuments(Context ctx) {
        List<Document> docs = documents.findByOwnerIdOrderByUploadedAtDesc(ctx.ownerId());
        if (docs.isEmpty()) {
            return new Result("L'utilisateur n'a encore importé aucun document.", "Liste des documents", "Aucun document");
        }
        StringBuilder sb = new StringBuilder();
        for (Document d : docs) {
            sb.append("- document_id=").append(d.getId()).append(" : ").append(d.getName())
              .append(" (").append(d.getPageCount()).append(" pages). Résumé : ").append(d.getSummary()).append('\n');
        }
        return new Result(sb.toString().trim(), "Liste des documents", docs.size() + " document" + (docs.size() > 1 ? "s" : ""));
    }

    private Result readDocument(JsonNode args, Context ctx) {
        long id = args.path("document_id").asLong(-1);
        Document doc = documents.findByIdAndOwnerId(id, ctx.ownerId()).orElse(null);
        if (doc == null) {
            return new Result("Document " + id + " introuvable. Utilise list_documents pour connaître les identifiants.", "Lecture", "Document introuvable");
        }
        int from = Math.max(1, args.path("page_from").asInt(1));
        int to = Math.min(doc.getPageCount(), args.path("page_to").asInt(from + READ_MAX_PAGES - 1));
        to = Math.min(to, from + READ_MAX_PAGES - 1);
        StringBuilder sb = new StringBuilder();
        for (DocumentPage p : pages.findByDocumentIdOrderByPageNumber(doc.getId())) {
            if (p.getPageNumber() < from || p.getPageNumber() > to) {
                continue;
            }
            int n = ctx.sources().add(doc.getId(), doc.getName(), p.getPageNumber(), p.getText());
            sb.append('[').append(n).append("] ").append(doc.getName()).append(", page ").append(p.getPageNumber()).append(" :\n")
              .append(p.getText()).append("\n\n");
            if (sb.length() > READ_MAX_CHARS) {
                sb.setLength(READ_MAX_CHARS);
                sb.append("\n[… texte tronqué]");
                break;
            }
        }
        String range = from == to ? "p. " + from : "p. " + from + " à " + to;
        if (sb.isEmpty()) {
            return new Result("Ces pages n'existent pas. Le document a " + doc.getPageCount() + " pages.", "Lecture : " + doc.getName(), "Pages inexistantes");
        }
        String more = to < doc.getPageCount() ? "\n(Le document continue jusqu'à la page " + doc.getPageCount() + ".)" : "";
        return new Result(sb.toString().trim() + more, "Lecture : " + doc.getName() + ", " + range, (to - from + 1) + " page" + (to > from ? "s" : "") + " lue" + (to > from ? "s" : ""));
    }

    private Result calculate(JsonNode args) {
        String expression = args.path("expression").asText("");
        try {
            String value = Calculator.format(Calculator.evaluate(expression));
            return new Result(expression + " = " + value, "Calcul : " + expression, "= " + value);
        } catch (IllegalArgumentException e) {
            return new Result("Calcul impossible : " + e.getMessage(), "Calcul : " + expression, "Erreur : " + e.getMessage());
        }
    }

    private Result createNote(JsonNode args, Context ctx) {
        String title = args.path("title").asText("").trim();
        String content = args.path("content").asText("").trim();
        if (title.isEmpty() || content.isEmpty()) {
            return new Result("Titre et contenu obligatoires.", "Note", "Erreur");
        }
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }
        Note note = notes.save(new Note(ctx.ownerId(), ctx.conversationId(), title, content));
        return new Result("Note enregistrée (id " + note.getId() + ").", "Note créée : " + title, "Enregistrée dans Notes");
    }

    /** Paramètres d'appel sous forme de Map, pour l'affichage des étapes. */
    public static Map<String, Object> asMap(JsonNode args) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : args.properties()) {
            JsonNode v = e.getValue();
            map.put(e.getKey(), v.isTextual() ? v.asText() : Json.MAPPER.convertValue(v, Object.class));
        }
        return map;
    }
}
