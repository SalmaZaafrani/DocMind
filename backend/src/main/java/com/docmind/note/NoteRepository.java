package com.docmind.note;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<Note> findByIdAndOwnerId(Long id, Long ownerId);
}
