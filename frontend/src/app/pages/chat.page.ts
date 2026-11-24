import { Component, ElementRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth.interceptor';
import { renderMarkdown } from '../core/markdown';
import { DocumentInfo, Message, Mode, Source } from '../core/models';

interface PendingQuestion {
  question: string;
  mode: Mode;
  documentIds: number[];
}

const SUGGESTIONS: { text: string; mode: Mode }[] = [
  { text: "Combien de jours de télétravail sont autorisés et quel est le montant de l'indemnité ?", mode: 'rag' },
  { text: 'Quel est le délai d\'intervention pour un incident critique ?', mode: 'rag' },
  { text: "Calcule la pénalité due par Sigma Services pour l'incident informatique du 8 août.", mode: 'agent' },
  { text: 'Résume les trois documents et enregistre une note avec les points à surveiller.', mode: 'agent' },
];

const WORKING_LABELS: Record<Mode, string[]> = {
  rag: ['Recherche des passages pertinents…', 'Rédaction de la réponse…'],
  agent: ['Analyse de la demande…', 'Recherche dans vos documents…', 'Lecture des passages…', 'Vérification et rédaction…'],
};

@Component({
  selector: 'app-chat',
  imports: [FormsModule, RouterLink],
  templateUrl: './chat.page.html',
})
export class ChatPage {
  private api = inject(ApiService);
  private router = inject(Router);

  /** Identifiant de conversation dans l'URL (/assistant/:id). */
  id = input<string>();

  readonly suggestions = SUGGESTIONS;
  conversationId = signal<number | null>(null);
  messages = signal<Message[]>([]);
  documents = signal<DocumentInfo[]>([]);
  selectedDocs = signal<number[]>([]);
  mode = signal<Mode>('agent');
  draft = '';
  sending = signal(false);
  loading = signal(false);
  error = signal('');
  workingLabel = signal('');
  activeMessageId = signal<number | null>(null);
  highlighted = signal<number | null>(null);
  openSteps = signal<Set<number>>(new Set());
  scopeOpen = signal(false);

  private scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  private workingTimer: ReturnType<typeof setInterval> | null = null;

  readonly title = computed(() => {
    const id = this.conversationId();
    return this.api.conversations().find((c) => c.id === id)?.title ?? 'Nouvelle conversation';
  });

  readonly activeMessage = computed<Message | null>(() => {
    const list = this.messages().filter((m) => m.role === 'assistant' && !m.pending);
    const id = this.activeMessageId();
    return list.find((m) => m.id === id) ?? list[list.length - 1] ?? null;
  });

  readonly scopeLabel = computed(() => {
    const ids = this.selectedDocs();
    if (!ids.length) return 'Tous les documents';
    if (ids.length === 1) return this.documents().find((d) => d.id === ids[0])?.name ?? '1 document';
    return `${ids.length} documents`;
  });

  readonly llmEnabled = computed(() => this.api.status()?.llm ?? true);

  constructor() {
    this.api.documents().subscribe({ next: (d) => this.documents.set(d), error: () => {} });

    effect(() => {
      const raw = this.id();
      const id = raw ? Number(raw) : null;
      this.conversationId.set(id);
      this.activeMessageId.set(null);
      this.highlighted.set(null);
      this.error.set('');
      if (id) {
        this.load(id);
      } else {
        this.messages.set([]);
      }
    });
  }

  private load(id: number): void {
    this.loading.set(true);
    this.api.messages(id).subscribe({
      next: (list) => {
        this.messages.set(list);
        this.loading.set(false);
        this.scrollToBottom();
        const pending = history.state?.pending as PendingQuestion | undefined;
        if (pending) {
          history.replaceState({ ...history.state, pending: undefined }, '');
          this.mode.set(pending.mode);
          this.selectedDocs.set(pending.documentIds);
          this.send(pending.question);
        }
      },
      error: (e) => {
        this.loading.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  useSuggestion(s: { text: string; mode: Mode }): void {
    this.mode.set(s.mode);
    this.send(s.text);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  send(text?: string): void {
    const question = (text ?? this.draft).trim();
    if (!question || this.sending()) return;
    this.draft = '';
    this.error.set('');
    const id = this.conversationId();
    if (!id) {
      // Nouvelle conversation : on la crée puis on pose la question depuis son URL
      this.sending.set(true);
      this.api.createConversation().subscribe({
        next: (c) => {
          this.sending.set(false);
          const pending: PendingQuestion = { question, mode: this.mode(), documentIds: this.selectedDocs() };
          this.router.navigate(['/assistant', c.id], { state: { pending } });
        },
        error: (e) => {
          this.sending.set(false);
          this.draft = question;
          this.error.set(errorMessage(e));
        },
      });
      return;
    }

    const mode = this.mode();
    const now = new Date().toISOString();
    const tempUser: Message = { id: -Date.now(), role: 'user', content: question, mode, model: null, sources: [], steps: [], durationMs: 0, createdAt: now };
    const tempReply: Message = { ...tempUser, id: -Date.now() - 1, role: 'assistant', content: '', pending: true };
    this.messages.update((list) => [...list, tempUser, tempReply]);
    this.sending.set(true);
    this.startWorking(mode);
    this.scrollToBottom();

    this.api.ask(id, question, mode, this.selectedDocs()).subscribe({
      next: ([user, reply]) => {
        this.messages.update((list) => [...list.filter((m) => m.id !== tempUser.id && m.id !== tempReply.id), user, reply]);
        this.activeMessageId.set(reply.id);
        this.highlighted.set(null);
        this.stopWorking();
        this.api.refreshConversations();
        this.scrollToBottom();
      },
      error: (e) => {
        this.messages.update((list) => list.filter((m) => m.id !== tempUser.id && m.id !== tempReply.id));
        this.draft = question;
        this.error.set(errorMessage(e));
        this.stopWorking();
      },
    });
  }

  html(m: Message): string {
    return renderMarkdown(m.content, new Set(m.sources.map((s) => s.n)));
  }

  onAnswerClick(m: Message, event: MouseEvent): void {
    const el = (event.target as HTMLElement).closest('.cite');
    if (!el) return;
    const match = el.className.match(/cite-(\d+)/);
    if (!match) return;
    this.focusSource(m, Number(match[1]));
  }

  focusSource(m: Message, n: number): void {
    this.activeMessageId.set(m.id);
    this.highlighted.set(n);
    setTimeout(() => document.getElementById(`source-${n}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }));
  }

  toggleSteps(id: number): void {
    this.openSteps.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  toggleDoc(id: number): void {
    this.selectedDocs.update((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  }

  sourceLink(s: Source): unknown[] {
    return ['/documents', s.documentId];
  }

  seconds(ms: number): string {
    return (ms / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' s';
  }

  stepIcon(tool: string): string {
    return { search_documents: '⌕', list_documents: '☰', read_document: '¶', calculate: '=', create_note: '✎' }[tool] ?? '•';
  }

  private startWorking(mode: Mode): void {
    const labels = WORKING_LABELS[mode];
    let i = 0;
    this.workingLabel.set(labels[0]);
    this.workingTimer = setInterval(() => {
      i = Math.min(i + 1, labels.length - 1);
      this.workingLabel.set(labels[i]);
    }, 2200);
  }

  private stopWorking(): void {
    this.sending.set(false);
    if (this.workingTimer) clearInterval(this.workingTimer);
    this.workingTimer = null;
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}
