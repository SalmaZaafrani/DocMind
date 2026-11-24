import { Component, inject, signal } from '@angular/core';
import { HttpEventType } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth.interceptor';
import { DocumentInfo } from '../core/models';

interface UploadItem {
  name: string;
  progress: number;
  state: 'upload' | 'processing' | 'done' | 'error';
  message?: string;
}

const ACCEPT = '.pdf,.docx,.txt,.md,.csv,.png,.jpg,.jpeg';

@Component({
  selector: 'app-documents',
  imports: [RouterLink],
  templateUrl: './documents.page.html',
})
export class DocumentsPage {
  private api = inject(ApiService);

  readonly accept = ACCEPT;
  documents = signal<DocumentInfo[]>([]);
  uploads = signal<UploadItem[]>([]);
  loading = signal(true);
  dragging = signal(false);
  error = signal('');
  confirmDelete = signal<number | null>(null);

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.api.documents().subscribe({
      next: (d) => {
        this.documents.set(d);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (event.dataTransfer?.files) this.uploadFiles(event.dataTransfer.files);
  }

  onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) this.uploadFiles(input.files);
    input.value = '';
  }

  private uploadFiles(files: FileList): void {
    Array.from(files).forEach((file) => this.uploadOne(file));
  }

  private uploadOne(file: File): void {
    const item: UploadItem = { name: file.name, progress: 0, state: 'upload' };
    this.uploads.update((list) => [item, ...list]);
    const update = (patch: Partial<UploadItem>) => {
      Object.assign(item, patch);
      this.uploads.update((list) => [...list]);
    };
    if (file.size > 20 * 1024 * 1024) {
      update({ state: 'error', message: 'Fichier trop volumineux (20 Mo maximum)' });
      return;
    }
    this.api.upload(file).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          const progress = Math.round((event.loaded / event.total) * 100);
          update({ progress, state: progress >= 100 ? 'processing' : 'upload' });
        } else if (event.type === HttpEventType.Response && event.body) {
          update({ state: 'done', progress: 100 });
          this.documents.update((list) => [event.body!, ...list]);
          setTimeout(() => this.uploads.update((list) => list.filter((u) => u !== item)), 2500);
        }
      },
      error: (e) => update({ state: 'error', message: errorMessage(e) }),
    });
  }

  dismiss(item: UploadItem): void {
    this.uploads.update((list) => list.filter((u) => u !== item));
  }

  remove(doc: DocumentInfo): void {
    this.api.deleteDocument(doc.id).subscribe({
      next: () => {
        this.documents.update((list) => list.filter((d) => d.id !== doc.id));
        this.confirmDelete.set(null);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  kind(doc: DocumentInfo): string {
    const ext = doc.name.split('.').pop()?.toUpperCase() ?? '';
    return ext.length <= 4 ? ext : 'FICHIER';
  }

  size(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} Ko`;
    return `${(bytes / 1024 / 1024).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} Mo`;
  }

  date(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
  }
}
