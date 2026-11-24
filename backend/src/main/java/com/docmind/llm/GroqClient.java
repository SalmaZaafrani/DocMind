package com.docmind.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docmind.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Client de l'API Groq (format compatible OpenAI), avec appels d'outils.
 * Si le modèle configuré n'existe plus, un modèle de chat disponible est choisi automatiquement.
 */
@Service
public class GroqClient {

    public record Reply(String content, ArrayNode toolCalls) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    private static final List<String> NOT_CHAT = List.of("whisper", "guard", "tts", "embed", "playai", "orpheus", "prompt", "compound");
    private static final List<String> PREFERRED = List.of("gpt-oss-120b", "llama-3.3-70b", "llama-4", "gpt-oss", "qwen", "kimi", "llama");

    private final String apiKey;
    private final String baseUrl;
    private volatile String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public GroqClient(@Value("${docmind.groq.api-key}") String apiKey,
                      @Value("${docmind.groq.model}") String model,
                      @Value("${docmind.groq.url}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (!isEnabled()) {
            log.warn("GROQ_API_KEY absente : DocMind répondra sans LLM (passages extraits uniquement)");
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    public static ObjectNode message(String role, String content) {
        ObjectNode m = Json.MAPPER.createObjectNode();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /** Réponse simple, sans outils. */
    public String complete(String system, String user, int maxTokens) {
        ArrayNode messages = Json.MAPPER.createArrayNode();
        messages.add(message("system", system));
        messages.add(message("user", user));
        return chat(messages, null, maxTokens).content();
    }

    public Reply chat(ArrayNode messages, ArrayNode tools, int maxTokens) {
        try {
            return send(messages, tools, maxTokens);
        } catch (LlmException e) {
            if (!isModelError(e)) {
                throw e;
            }
            String replacement = pickModel();
            if (replacement == null || replacement.equals(model)) {
                throw e;
            }
            log.warn("Modèle {} indisponible chez Groq, utilisation de {}", model, replacement);
            model = replacement;
            return send(messages, tools, maxTokens);
        }
    }

    private Reply send(ArrayNode messages, ArrayNode tools, int maxTokens) {
        if (!isEnabled()) {
            throw new LlmException(0, "Aucune clé GROQ_API_KEY configurée");
        }
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", maxTokens);
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        JsonNode json = post("/chat/completions", Json.write(body));
        JsonNode msg = json.path("choices").path(0).path("message");
        String content = msg.path("content").isTextual() ? msg.path("content").asText() : "";
        ArrayNode calls = msg.path("tool_calls").isArray() ? (ArrayNode) msg.path("tool_calls") : null;
        return new Reply(content, calls);
    }

    private JsonNode post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return execute(request);
    }

    private JsonNode execute(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new LlmException(response.statusCode(), response.body());
            }
            return Json.MAPPER.readTree(response.body());
        } catch (LlmException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(0, "Requête interrompue");
        } catch (Exception e) {
            throw new LlmException(0, "Groq injoignable : " + e.getMessage());
        }
    }

    private static boolean isModelError(LlmException e) {
        String m = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        return e.getStatus() == 404 || m.contains("model_not_found") || m.contains("decommissioned") || m.contains("does not exist");
    }

    private String pickModel() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            List<String> ids = new ArrayList<>();
            for (JsonNode m : execute(request).path("data")) {
                String id = m.path("id").asText();
                String lower = id.toLowerCase(Locale.ROOT);
                if (NOT_CHAT.stream().noneMatch(lower::contains)) {
                    ids.add(id);
                }
            }
            ids.sort(String::compareTo);
            for (String pref : PREFERRED) {
                for (String id : ids) {
                    if (id.toLowerCase(Locale.ROOT).contains(pref)) {
                        return id;
                    }
                }
            }
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.warn("Liste des modèles Groq indisponible : {}", e.getMessage());
            return null;
        }
    }
}
