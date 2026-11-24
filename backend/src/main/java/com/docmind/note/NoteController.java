package com.docmind.note;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docmind.auth.CurrentUser;
import com.docmind.common.ApiException;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    public record NoteDto(Long id, Long conversationId, String title, String content, String createdAt) {
        static NoteDto of(Note n) {
            return new NoteDto(n.getId(), n.getConversationId(), n.getTitle(), n.getContent(), n.getCreatedAt().toString());
        }
    }

    private final NoteRepository notes;

    public NoteController(NoteRepository notes) {
        this.notes = notes;
    }

    @GetMapping
    public List<NoteDto> list() {
        return notes.findByOwnerIdOrderByCreatedAtDesc(CurrentUser.get().getId()).stream().map(NoteDto::of).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Note note = notes.findByIdAndOwnerId(id, CurrentUser.get().getId()).orElseThrow(() -> ApiException.notFound("Note"));
        notes.delete(note);
        return ResponseEntity.noContent().build();
    }
}
