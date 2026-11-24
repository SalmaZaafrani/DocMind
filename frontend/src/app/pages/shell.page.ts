import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.page.html',
})
export class ShellPage {
  api = inject(ApiService);
  auth = inject(AuthService);
  private router = inject(Router);

  menuOpen = signal(false);

  constructor() {
    this.api.refreshConversations();
    this.api.loadStatus();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.menuOpen.set(false));
  }

  newConversation(): void {
    this.router.navigate(['/assistant']);
  }

  remove(id: number, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.api.deleteConversation(id).subscribe(() => {
      if (this.router.url === `/assistant/${id}`) this.router.navigate(['/assistant']);
    });
  }
}
