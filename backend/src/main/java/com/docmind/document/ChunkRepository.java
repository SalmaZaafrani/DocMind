package com.docmind.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    List<Chunk> findByOwnerId(Long ownerId);

    @Transactional
    void deleteByDocumentId(Long documentId);
}
