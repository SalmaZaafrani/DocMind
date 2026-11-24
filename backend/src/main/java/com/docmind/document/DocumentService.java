package com.docmind.document;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.docmind.common.ApiException;
import com.docmind.llm.GroqClient;
import com.docmind.search.SearchService;

/** Import d'un document : extraction du texte, découpage en passages, résumé, indexation. */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documents;
    private final DocumentPageRepository pages;
    private final ChunkRepository chunks;
    private final TextExtractor extractor;
    private final SearchService search;
    private final GroqClient llm;

    public DocumentService(DocumentRepository documents, DocumentPageRepository pages, ChunkRepository chunks,
                           TextExtractor extractor, SearchService search, GroqClient llm) {
        this.documents = documents;
        this.pages = pages;
        this.chunks = chunks;
        this.extractor = extractor;
        this.search = search;
        this.llm = llm;
    }

    public Document ingest(Long ownerId, String filename, String contentType, byte[] data) {
        if (filename == null || filename.isBlank()) {
            throw ApiException.badRequest("Nom de fichier manquant");
        }
        if (!TextExtractor.isSupported(filename)) {
            throw ApiException.badRequest("Format non pris en charge. Formats acceptés : PDF, DOCX, TXT, MD, CSV, PNG, JPG");
        }
        TextExtractor.Extraction extraction = extractor.extract(filename, data);
        String fullText = String.join("\n\n", extraction.pages()).trim();
        if (fullText.length() < 10) {
            throw ApiException.badRequest("Aucun texte lisible n'a été trouvé dans ce fichier");
        }

        Document doc = new Document(ownerId, filename, contentType == null ? "application/octet-stream" : contentType, data.length);
        doc.setOcrUsed(extraction.ocrUsed());
        doc = documents.save(doc);

        List<DocumentPage> pageEntities = new ArrayList<>();
        List<Chunk> chunkEntities = new ArrayList<>();
        int position = 0;
        for (int i = 0; i < extraction.pages().size(); i++) {
            String text = extraction.pages().get(i);
            pageEntities.add(new DocumentPage(doc.getId(), i + 1, text));
            for (String piece : Chunker.split(text)) {
                chunkEntities.add(new Chunk(doc.getId(), ownerId, i + 1, position++, piece));
            }
        }
        pages.saveAll(pageEntities);
        chunks.saveAll(chunkEntities);

        doc.setPageCount(pageEntities.size());
        doc.setChunkCount(chunkEntities.size());
        doc.setWordCount(fullText.split("\\s+").length);
        doc.setSummary(summarize(filename, fullText));
        doc = documents.save(doc);

        search.invalidate(ownerId);
        log.info("Document importé : {} ({} pages, {} passages)", filename, doc.getPageCount(), doc.getChunkCount());
        return doc;
    }

    public void delete(Long ownerId, Long documentId) {
        Document doc = get(ownerId, documentId);
        chunks.deleteByDocumentId(doc.getId());
        pages.deleteByDocumentId(doc.getId());
        documents.delete(doc);
        search.invalidate(ownerId);
    }

    public Document get(Long ownerId, Long documentId) {
        return documents.findByIdAndOwnerId(documentId, ownerId).orElseThrow(() -> ApiException.notFound("Document"));
    }

    public List<DocumentPage> pages(Long ownerId, Long documentId) {
        get(ownerId, documentId);
        return pages.findByDocumentIdOrderByPageNumber(documentId);
    }

    private String summarize(String filename, String text) {
        String excerpt = text.length() > 6000 ? text.substring(0, 6000) : text;
        if (llm.isEnabled()) {
            try {
                String summary = llm.complete(
                        "Tu résumes des documents professionnels. Réponds en 2 phrases maximum, en français, sans préambule.",
                        "Document : " + filename + "\n\n" + excerpt, 220);
                if (summary != null && !summary.isBlank()) {
                    return summary.trim();
                }
            } catch (Exception e) {
                log.warn("Résumé LLM impossible pour {} : {}", filename, e.getMessage());
            }
        }
        String flat = excerpt.replaceAll("(?m)^#{1,6}\\s*", "").replace("**", "").replaceAll("\\s+", " ").trim();
        return flat.length() > 280 ? flat.substring(0, 280).trim() + "…" : flat;
    }
}
