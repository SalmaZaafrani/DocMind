package com.docmind.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    Optional<Conversation> findByIdAndOwnerId(Long id, Long ownerId);
}
