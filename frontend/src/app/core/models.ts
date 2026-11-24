export interface User {
  id: number;
  email: string;
  fullName: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface DocumentInfo {
  id: number;
  name: string;
  contentType: string;
  sizeBytes: number;
  pageCount: number;
  chunkCount: number;
  wordCount: number;
  ocrUsed: boolean;
  summary: string;
  uploadedAt: string;
}

export interface DocumentDetail {
  document: DocumentInfo;
  pages: { page: number; text: string }[];
}

export interface Conversation {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface Source {
  n: number;
  documentId: number;
  documentName: string;
  page: number;
  excerpt: string;
}

export interface Step {
  tool: string;
  label: string;
  input: Record<string, unknown>;
  output: string;
  durationMs: number;
}

export type Mode = 'rag' | 'agent';

export interface Message {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  mode: Mode;
  model: string | null;
  sources: Source[];
  steps: Step[];
  durationMs: number;
  createdAt: string;
  pending?: boolean;
}

export interface Note {
  id: number;
  conversationId: number | null;
  title: string;
  content: string;
  createdAt: string;
}

export interface SystemStatus {
  status: string;
  llm: boolean;
  model: string | null;
  ocr: boolean;
}
