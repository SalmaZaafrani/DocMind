package com.docmind.chat;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages", indexes = @Index(columnList = "conversation_id"))
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    /** "user" ou "assistant". */
    @Column(nullable = false)
    private String role;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    /** "rag" ou "agent". */
    private String mode;

    /** Modèle utilisé, ou "sans LLM". */
    private String model;

    @Column(columnDefinition = "text")
    private String sourcesJson;

    @Column(columnDefinition = "text")
    private String stepsJson;

    private long durationMs;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Message() {
    }

    public Message(Long conversationId, String role, String content, String mode) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.mode = mode;
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getMode() { return mode; }
    public String getModel() { return model; }
    public String getSourcesJson() { return sourcesJson; }
    public String getStepsJson() { return stepsJson; }
    public long getDurationMs() { return durationMs; }
    public Instant getCreatedAt() { return createdAt; }

    public void setModel(String model) { this.model = model; }
    public void setSourcesJson(String sourcesJson) { this.sourcesJson = sourcesJson; }
    public void setStepsJson(String stepsJson) { this.stepsJson = stepsJson; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
