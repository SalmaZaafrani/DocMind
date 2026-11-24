package com.docmind.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.docmind.common.Json;
import com.docmind.llm.GroqClient;
import com.docmind.llm.LlmException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Mode agent : le modèle décide lui-même des outils à appeler (recherche, lecture, calcul, note),
 * observe les résultats et recommence jusqu'à pouvoir répondre. Chaque action est enregistrée.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_STEPS = 6;

    private final GroqClient llm;
    private final AgentTools tools;
    private final RagService rag;

    public AgentService(GroqClient llm, AgentTools tools, RagService rag) {
        this.llm = llm;
        this.tools = tools;
        this.rag = rag;
    }

    public Answer run(Long ownerId, Long conversationId, List<ObjectNode> history, String question, Set<Long> documentFilter) {
        if (!llm.isEnabled()) {
            Answer fallback = rag.answer(ownerId, history, question, documentFilter);
            return new Answer(fallback.content() + "\n\n_Le mode agent nécessite une clé GROQ_API_KEY : réponse rapide utilisée._",
                    fallback.sources(), fallback.steps(), fallback.model());
        }

        SourceCollector sources = new SourceCollector();
        AgentTools.Context ctx = new AgentTools.Context(ownerId, conversationId, documentFilter, sources);
        List<Step> steps = new ArrayList<>();

        ArrayNode messages = Json.MAPPER.createArrayNode();
        String system = Prompts.AGENT;
        if (documentFilter != null && !documentFilter.isEmpty()) {
            system += "\nL'utilisateur a sélectionné les documents " + documentFilter + " : concentre-toi sur eux.";
        }
        messages.add(GroqClient.message("system", system));
        history.forEach(messages::add);
        messages.add(GroqClient.message("user", question));

        try {
            for (int i = 0; i < MAX_STEPS; i++) {
                GroqClient.Reply reply = llm.chat(messages, tools.definitions(), 1500);
                if (!reply.hasToolCalls()) {
                    return finish(reply.content(), sources, steps);
                }
                messages.add(assistantWithCalls(reply));
                for (JsonNode call : reply.toolCalls()) {
                    messages.add(runTool(call, ctx, steps));
                }
            }
            // Trop d'étapes : on demande la réponse finale avec ce qui a été trouvé
            messages.add(GroqClient.message("user", "Rédige maintenant ta réponse finale avec les informations déjà obtenues."));
            return finish(llm.chat(messages, null, 1500).content(), sources, steps);
        } catch (LlmException e) {
            log.warn("Agent interrompu ({}): {}", e.getStatus(), e.getMessage());
            if (!sources.isEmpty()) {
                try {
                    messages.add(GroqClient.message("user", "Rédige ta réponse finale avec les informations déjà obtenues, sans appeler d'outil."));
                    return finish(llm.chat(messages, null, 1500).content(), sources, steps);
                } catch (Exception ignored) {
                    // On passe au mode rapide
                }
            }
            Answer fallback = rag.answer(ownerId, history, question, documentFilter);
            List<Step> all = new ArrayList<>(steps);
            all.addAll(fallback.steps());
            return new Answer(fallback.content(), fallback.sources(), all, fallback.model());
        }
    }

    private Answer finish(String content, SourceCollector sources, List<Step> steps) {
        String text = content == null || content.isBlank()
                ? "Je n'ai pas pu formuler de réponse. Pouvez-vous reformuler la question ?"
                : SourceCollector.normalizeCitations(content.trim());
        return new Answer(text, sources.citedIn(text), steps, llm.model());
    }

    private ObjectNode assistantWithCalls(GroqClient.Reply reply) {
        ObjectNode msg = Json.MAPPER.createObjectNode();
        msg.put("role", "assistant");
        msg.put("content", reply.content() == null ? "" : reply.content());
        ArrayNode calls = msg.putArray("tool_calls");
        for (JsonNode call : reply.toolCalls()) {
            ObjectNode c = calls.addObject();
            c.put("id", call.path("id").asText());
            c.put("type", "function");
            ObjectNode fn = c.putObject("function");
            fn.put("name", call.path("function").path("name").asText());
            fn.put("arguments", arguments(call));
        }
        return msg;
    }

    /** Arguments d'un appel d'outil : chaîne JSON (format OpenAI) ou objet selon le fournisseur. */
    private static String arguments(JsonNode call) {
        JsonNode args = call.path("function").path("arguments");
        if (args.isTextual()) {
            return args.asText().isBlank() ? "{}" : args.asText();
        }
        return args.isObject() ? args.toString() : "{}";
    }

    private ObjectNode runTool(JsonNode call, AgentTools.Context ctx, List<Step> steps) {
        String name = call.path("function").path("name").asText();
        String rawArgs = arguments(call);
        long start = System.currentTimeMillis();
        JsonNode args;
        AgentTools.Result result;
        try {
            args = Json.MAPPER.readTree(rawArgs.isBlank() ? "{}" : rawArgs);
            result = tools.execute(name, args, ctx);
        } catch (Exception e) {
            args = Json.MAPPER.createObjectNode();
            result = new AgentTools.Result("Paramètres invalides : " + e.getMessage(), name, "Erreur de paramètres");
        }
        steps.add(new Step(name, result.label(), AgentTools.asMap(args), result.summary(), System.currentTimeMillis() - start));

        ObjectNode msg = Json.MAPPER.createObjectNode();
        msg.put("role", "tool");
        msg.put("tool_call_id", call.path("id").asText());
        msg.put("content", result.forModel());
        return msg;
    }
}
