import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Conversation, DocumentDetail, DocumentInfo, Message, Mode, Note, SystemStatus } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  /** Conversations partagées entre la barre latérale et la page de discussion. */
  readonly conversations = signal<Conversation[]>([]);
  readonly status = signal<SystemStatus | null>(null);

  loadStatus(): void {
    this.http.get<SystemStatus>('/api/system/status').subscribe({ next: (s) => this.status.set(s), error: () => {} });
  }

  // Documents
  documents(): Observable<DocumentInfo[]> {
    return this.http.get<DocumentInfo[]>('/api/documents');
  }

  document(id: number): Observable<DocumentDetail> {
    return this.http.get<DocumentDetail>(`/api/documents/${id}`);
  }

  upload(file: File): Observable<HttpEvent<DocumentInfo>> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<DocumentInfo>('/api/documents', form, { reportProgress: true, observe: 'events' });
  }

  deleteDocument(id: number): Observable<void> {
    return this.http.delete<void>(`/api/documents/${id}`);
  }

  // Conversations
  refreshConversations(): void {
    this.http.get<Conversation[]>('/api/conversations').subscribe({ next: (c) => this.conversations.set(c), error: () => {} });
  }

  createConversation(): Observable<Conversation> {
    return this.http
      .post<Conversation>('/api/conversations', {})
      .pipe(tap((c) => this.conversations.update((list) => [c, ...list])));
  }

  deleteConversation(id: number): Observable<void> {
    return this.http
      .delete<void>(`/api/conversations/${id}`)
      .pipe(tap(() => this.conversations.update((list) => list.filter((c) => c.id !== id))));
  }

  messages(conversationId: number): Observable<Message[]> {
    return this.http.get<Message[]>(`/api/conversations/${conversationId}/messages`);
  }

  ask(conversationId: number, content: string, mode: Mode, documentIds: number[]): Observable<Message[]> {
    return this.http.post<Message[]>(`/api/conversations/${conversationId}/messages`, { content, mode, documentIds });
  }

  // Notes
  notes(): Observable<Note[]> {
    return this.http.get<Note[]>('/api/notes');
  }

  deleteNote(id: number): Observable<void> {
    return this.http.delete<void>(`/api/notes/${id}`);
  }
}
