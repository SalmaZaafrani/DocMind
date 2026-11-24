package com.docmind.chat;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docmind.auth.CurrentUser;
import com.docmind.common.Json;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {

    public record ConversationDto(Long id, String title, String createdAt, String updatedAt) {
        static ConversationDto of(Conversation c) {
            return new ConversationDto(c.getId(), c.getTitle(), c.getCreatedAt().toString(), c.getUpdatedAt().toString());
        }
    }

    public record MessageDto(Long id, String role, String content, String mode, String model,
                             List<Map<String, Object>> sources, List<Map<String, Object>> steps,
                             long durationMs, String createdAt) {
        static MessageDto of(Message m) {
            return new MessageDto(m.getId(), m.getRole(), m.getContent(), m.getMode(), m.getModel(),
                    Json.readList(m.getSourcesJson()), Json.readList(m.getStepsJson()), m.getDurationMs(),
                    m.getCreatedAt().toString());
        }
    }

    public record CreateRequest(String title) { }

    public record AskRequest(String content, String mode, List<Long> documentIds) { }

    private final ChatService chat;
    private final ConversationRepository conversations;

    public ChatController(ChatService chat, ConversationRepository conversations) {
        this.chat = chat;
        this.conversations = conversations;
    }

    @GetMapping
    public List<ConversationDto> list() {
        return conversations.findByOwnerIdOrderByUpdatedAtDesc(CurrentUser.get().getId()).stream().map(ConversationDto::of).toList();
    }

    @PostMapping
    public ConversationDto create(@RequestBody(required = false) CreateRequest req) {
        return ConversationDto.of(chat.create(CurrentUser.get().getId(), req == null ? null : req.title()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        chat.delete(CurrentUser.get().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> messages(@PathVariable Long id) {
        return chat.messages(CurrentUser.get().getId(), id).stream().map(MessageDto::of).toList();
    }

    @PostMapping("/{id}/messages")
    public List<MessageDto> ask(@PathVariable Long id, @RequestBody AskRequest req) {
        return chat.ask(CurrentUser.get().getId(), id, req.content(), req.mode(), req.documentIds())
                .stream().map(MessageDto::of).toList();
    }
}
