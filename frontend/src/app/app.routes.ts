import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'connexion', loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/shell.page').then((m) => m.ShellPage),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'assistant' },
      { path: 'assistant', loadComponent: () => import('./pages/chat.page').then((m) => m.ChatPage) },
      { path: 'assistant/:id', loadComponent: () => import('./pages/chat.page').then((m) => m.ChatPage) },
      { path: 'documents', loadComponent: () => import('./pages/documents.page').then((m) => m.DocumentsPage) },
      { path: 'documents/:id', loadComponent: () => import('./pages/viewer.page').then((m) => m.ViewerPage) },
      { path: 'notes', loadComponent: () => import('./pages/notes.page').then((m) => m.NotesPage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
