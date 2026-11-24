package com.docmind.note;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Note enregistrée par l'agent à la demande de l'utilisateur (synthèse, liste d'actions…). */
@Entity
@Table(name = "notes", indexes = @Index(columnList = "owner_id"))
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    private Long conversationId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Note() {
    }

    public Note(Long ownerId, Long conversationId, String title, String content) {
        this.ownerId = ownerId;
        this.conversationId = conversationId;
        this.title = title;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public Long getConversationId() { return conversationId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
