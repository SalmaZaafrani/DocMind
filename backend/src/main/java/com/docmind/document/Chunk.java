package com.docmind.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Passage d'environ 900 caractères, unité de recherche du RAG. */
@Entity
@Table(name = "chunks", indexes = { @Index(columnList = "document_id"), @Index(columnList = "owner_id") })
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private Long ownerId;

    private int pageNumber;
    private int position;

    @Column(columnDefinition = "text", nullable = false)
    private String text;

    protected Chunk() {
    }

    public Chunk(Long documentId, Long ownerId, int pageNumber, int position, String text) {
        this.documentId = documentId;
        this.ownerId = ownerId;
        this.pageNumber = pageNumber;
        this.position = position;
        this.text = text;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public Long getOwnerId() { return ownerId; }
    public int getPageNumber() { return pageNumber; }
    public int getPosition() { return position; }
    public String getText() { return text; }
}
