package com.docmind.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Texte d'une page (PDF) ou d'une section (autres formats) d'un document. */
@Entity
@Table(name = "document_pages", indexes = @Index(columnList = "document_id"))
public class DocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    private int pageNumber;

    @Column(columnDefinition = "text", nullable = false)
    private String text;

    protected DocumentPage() {
    }

    public DocumentPage(Long documentId, int pageNumber, String text) {
        this.documentId = documentId;
        this.pageNumber = pageNumber;
        this.text = text;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public int getPageNumber() { return pageNumber; }
    public String getText() { return text; }
}
