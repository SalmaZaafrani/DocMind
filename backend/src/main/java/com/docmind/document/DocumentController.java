package com.docmind.document;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docmind.auth.CurrentUser;
import com.docmind.common.ApiException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    public record DocumentDto(Long id, String name, String contentType, long sizeBytes, int pageCount, int chunkCount,
                              int wordCount, boolean ocrUsed, String summary, String uploadedAt) {
        public static DocumentDto of(Document d) {
            return new DocumentDto(d.getId(), d.getName(), d.getContentType(), d.getSizeBytes(), d.getPageCount(),
                    d.getChunkCount(), d.getWordCount(), d.isOcrUsed(), d.getSummary(), d.getUploadedAt().toString());
        }
    }

    public record PageDto(int page, String text) { }

    public record DocumentDetail(DocumentDto document, List<PageDto> pages) { }

    private final DocumentService service;
    private final DocumentRepository documents;

    public DocumentController(DocumentService service, DocumentRepository documents) {
        this.service = service;
        this.documents = documents;
    }

    @GetMapping
    public List<DocumentDto> list() {
        return documents.findByOwnerIdOrderByUploadedAtDesc(CurrentUser.get().getId()).stream().map(DocumentDto::of).toList();
    }

    @PostMapping
    public DocumentDto upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier vide");
        }
        try {
            Document doc = service.ingest(CurrentUser.get().getId(), file.getOriginalFilename(), file.getContentType(), file.getBytes());
            return DocumentDto.of(doc);
        } catch (IOException e) {
            throw ApiException.badRequest("Lecture du fichier impossible");
        }
    }

    @GetMapping("/{id}")
    public DocumentDetail detail(@PathVariable Long id) {
        Long owner = CurrentUser.get().getId();
        Document doc = service.get(owner, id);
        List<PageDto> pages = service.pages(owner, id).stream().map(p -> new PageDto(p.getPageNumber(), p.getText())).toList();
        return new DocumentDetail(DocumentDto.of(doc), pages);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(CurrentUser.get().getId(), id);
        return ResponseEntity.noContent().build();
    }
}
