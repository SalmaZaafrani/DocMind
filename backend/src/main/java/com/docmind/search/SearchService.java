package com.docmind.search;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.docmind.document.ChunkRepository;
import com.docmind.document.Document;
import com.docmind.document.DocumentRepository;

/** Recherche dans les documents d'un utilisateur. L'index est reconstruit après chaque ajout ou suppression. */
@Service
public class SearchService {

    private final ChunkRepository chunks;
    private final DocumentRepository documents;
    private final Map<Long, Bm25Index> cache = new ConcurrentHashMap<>();

    public SearchService(ChunkRepository chunks, DocumentRepository documents) {
        this.chunks = chunks;
        this.documents = documents;
    }

    public List<SearchHit> search(Long ownerId, String query, int topK, Set<Long> onlyDocuments) {
        return index(ownerId).search(query, topK, onlyDocuments);
    }

    public void invalidate(Long ownerId) {
        cache.remove(ownerId);
    }

    private Bm25Index index(Long ownerId) {
        return cache.computeIfAbsent(ownerId, id -> {
            Map<Long, String> names = documents.findByOwnerIdOrderByUploadedAtDesc(id).stream()
                    .collect(Collectors.toMap(Document::getId, Document::getName, (a, b) -> a));
            List<Bm25Index.Entry> entries = chunks.findByOwnerId(id).stream()
                    .filter(c -> names.containsKey(c.getDocumentId()))
                    .map(c -> new Bm25Index.Entry(c.getId(), c.getDocumentId(), names.get(c.getDocumentId()),
                            c.getPageNumber(), c.getText()))
                    .toList();
            return new Bm25Index(entries);
        });
    }
}
