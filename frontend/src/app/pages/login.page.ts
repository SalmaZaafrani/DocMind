import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/auth.interceptor';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.page.html',
})
export class LoginPage {
  private auth = inject(AuthService);
  private router = inject(Router);

  mode = signal<'login' | 'register'>('login');
  fullName = '';
  email = '';
  password = '';
  error = signal('');
  loading = signal(false);

  constructor() {
    if (this.auth.isLoggedIn()) this.router.navigate(['/assistant']);
  }

  switchMode(): void {
    this.mode.update((m) => (m === 'login' ? 'register' : 'login'));
    this.error.set('');
  }

  demo(): void {
    this.mode.set('login');
    this.email = 'demo@docmind.app';
    this.password = 'demo1234';
    this.submit();
  }

  submit(): void {
    if (!this.email.trim() || !this.password || (this.mode() === 'register' && !this.fullName.trim())) {
      this.error.set('Remplissez tous les champs.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    const request =
      this.mode() === 'login'
        ? this.auth.login(this.email.trim(), this.password)
        : this.auth.register(this.fullName.trim(), this.email.trim(), this.password);
    request.subscribe({
      next: () => this.router.navigate(['/assistant']),
      error: (e) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }
}
