import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { errorMessage } from '../core/auth.interceptor';
import { DocumentDetail } from '../core/models';

@Component({
  selector: 'app-viewer',
  imports: [RouterLink],
  templateUrl: './viewer.page.html',
})
export class ViewerPage {
  private api = inject(ApiService);

  id = input.required<string>();
  page = input<string>();

  detail = signal<DocumentDetail | null>(null);
  error = signal('');

  constructor() {
    effect(() => {
      const id = Number(this.id());
      this.api.document(id).subscribe({
        next: (d) => {
          this.detail.set(d);
          const target = this.page();
          if (target) setTimeout(() => document.getElementById(`page-${target}`)?.scrollIntoView({ block: 'start' }), 50);
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
    });
  }

  isTarget(n: number): boolean {
    return String(n) === this.page();
  }
}
