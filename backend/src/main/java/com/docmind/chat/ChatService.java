package com.docmind.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.docmind.agent.AgentService;
import com.docmind.agent.Answer;
import com.docmind.agent.RagService;
import com.docmind.common.ApiException;
import com.docmind.common.Json;
import com.docmind.llm.GroqClient;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class ChatService {

    public static final String DEFAULT_TITLE = "Nouvelle conversation";
    private static final int HISTORY_MESSAGES = 6;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final RagService rag;
    private final AgentService agent;

    public ChatService(ConversationRepository conversations, MessageRepository messages, RagService rag, AgentService agent) {
        this.conversations = conversations;
        this.messages = messages;
        this.rag = rag;
        this.agent = agent;
    }

    public Conversation get(Long ownerId, Long id) {
        return conversations.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> ApiException.notFound("Conversation"));
    }

    public Conversation create(Long ownerId, String title) {
        String t = title == null || title.isBlank() ? DEFAULT_TITLE : title.trim();
        return conversations.save(new Conversation(ownerId, t.length() > 120 ? t.substring(0, 120) : t));
    }

    public void delete(Long ownerId, Long id) {
        Conversation c = get(ownerId, id);
        messages.deleteByConversationId(c.getId());
        conversations.delete(c);
    }

    public List<Message> messages(Long ownerId, Long id) {
        return messages.findByConversationIdOrderByCreatedAtAsc(get(ownerId, id).getId());
    }

    /** Enregistre la question, produit la réponse (RAG ou agent) et l'enregistre. */
    public List<Message> ask(Long ownerId, Long conversationId, String content, String mode, List<Long> documentIds) {
        Conversation conversation = get(ownerId, conversationId);
        String question = content == null ? "" : content.trim();
        if (question.isEmpty()) {
            throw ApiException.badRequest("La question est vide");
        }
        if (question.length() > 4000) {
            throw ApiException.badRequest("La question est trop longue (4 000 caractères maximum)");
        }
        String m = "agent".equalsIgnoreCase(mode) ? "agent" : "rag";
        Set<Long> filter = documentIds == null ? Set.of() : new HashSet<>(documentIds);

        List<ObjectNode> history = history(conversation.getId());
        Message userMessage = messages.save(new Message(conversation.getId(), "user", question, m));

        long start = System.currentTimeMillis();
        Answer answer = m.equals("agent")
                ? agent.run(ownerId, conversation.getId(), history, question, filter)
                : rag.answer(ownerId, history, question, filter);

        Message reply = new Message(conversation.getId(), "assistant", answer.content(), m);
        reply.setModel(answer.model());
        reply.setSourcesJson(Json.write(answer.sources()));
        reply.setStepsJson(Json.write(answer.steps()));
        reply.setDurationMs(System.currentTimeMillis() - start);
        reply = messages.save(reply);

        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            String title = question.replaceAll("\\s+", " ");
            conversation.setTitle(title.length() > 60 ? title.substring(0, 60).trim() + "…" : title);
        }
        conversation.touch();
        conversations.save(conversation);
        return List.of(userMessage, reply);
    }

    private List<ObjectNode> history(Long conversationId) {
        List<Message> all = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<ObjectNode> out = new ArrayList<>();
        for (Message msg : all.subList(Math.max(0, all.size() - HISTORY_MESSAGES), all.size())) {
            String text = msg.getContent();
            if (text.length() > 1500) {
                text = text.substring(0, 1500) + "…";
            }
            out.add(GroqClient.message(msg.getRole().equals("assistant") ? "assistant" : "user", text));
        }
        return out;
    }
}
