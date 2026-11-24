package com.docmind.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents", indexes = @Index(columnList = "owner_id"))
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String contentType;

    private long sizeBytes;
    private int pageCount;
    private int chunkCount;
    private int wordCount;
    private boolean ocrUsed;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    protected Document() {
    }

    public Document(Long ownerId, String name, String contentType, long sizeBytes) {
        this.ownerId = ownerId;
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getPageCount() { return pageCount; }
    public int getChunkCount() { return chunkCount; }
    public int getWordCount() { return wordCount; }
    public boolean isOcrUsed() { return ocrUsed; }
    public String getSummary() { return summary; }
    public Instant getUploadedAt() { return uploadedAt; }

    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }
    public void setOcrUsed(boolean ocrUsed) { this.ocrUsed = ocrUsed; }
    public void setSummary(String summary) { this.summary = summary; }
}
