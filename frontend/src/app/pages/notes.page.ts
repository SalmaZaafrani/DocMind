import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth.interceptor';
import { renderMarkdown } from '../core/markdown';
import { Note } from '../core/models';

@Component({
  selector: 'app-notes',
  imports: [RouterLink],
  templateUrl: './notes.page.html',
})
export class NotesPage {
  private api = inject(ApiService);

  notes = signal<Note[]>([]);
  loading = signal(true);
  error = signal('');
  copied = signal<number | null>(null);

  constructor() {
    this.api.notes().subscribe({
      next: (n) => {
        this.notes.set(n);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  html(n: Note): string {
    return renderMarkdown(n.content);
  }

  copy(n: Note): void {
    navigator.clipboard?.writeText(`${n.title}\n\n${n.content}`).then(() => {
      this.copied.set(n.id);
      setTimeout(() => this.copied.set(null), 1500);
    });
  }

  remove(n: Note): void {
    this.api.deleteNote(n.id).subscribe({
      next: () => this.notes.update((list) => list.filter((x) => x.id !== n.id)),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  date(iso: string): string {
    return new Date(iso).toLocaleString('fr-FR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  }
}
