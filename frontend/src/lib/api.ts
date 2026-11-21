import { getAccessToken, login } from './auth';
import type { Agent, ChatMessage, Conversation, KnowledgeDocument } from './types';

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

async function authorizedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = await getAccessToken();
  if (!token) {
    return login();
  }
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(path, { ...init, headers });
  if (response.status === 401) {
    return login();
  }
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try {
      const body = await response.json();
      detail = body.detail ?? body.title ?? detail;
    } catch {
      /* kein JSON */
    }
    if (response.status === 403) detail = 'Keine Berechtigung fuer diese Aktion';
    throw new ApiError(detail, response.status);
  }
  return response;
}

async function json<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await authorizedFetch(path, init);
  return (response.status === 204 ? undefined : await response.json()) as T;
}

const jsonBody = (body: unknown): RequestInit => ({
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export const api = {
  agents: () => json<Agent[]>('/api/agents'),

  conversations: () => json<Conversation[]>('/api/conversations'),
  createConversation: (agentId: string) =>
    json<Conversation>('/api/conversations', { method: 'POST', ...jsonBody({ agentId }) }),
  renameConversation: (id: string, title: string) =>
    json<Conversation>(`/api/conversations/${id}`, { method: 'PATCH', ...jsonBody({ title }) }),
  deleteConversation: (id: string) => json<void>(`/api/conversations/${id}`, { method: 'DELETE' }),
  messages: (id: string) => json<ChatMessage[]>(`/api/conversations/${id}/messages`),

  documents: (agentId: string) => json<KnowledgeDocument[]>(`/api/agents/${agentId}/documents`),
  uploadDocument: (agentId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return json<KnowledgeDocument>(`/api/agents/${agentId}/documents`, { method: 'POST', body: form });
  },
  deleteDocument: (agentId: string, documentId: string) =>
    json<void>(`/api/agents/${agentId}/documents/${documentId}`, { method: 'DELETE' }),
};

export interface StreamHandlers {
  onToken: (text: string) => void;
  onDone: (message: ChatMessage) => void;
  onError: (message: string) => void;
}

/**
 * Sendet eine Nachricht und liest die Antwort als Server-Sent Events.
 * EventSource unterstuetzt weder POST noch Authorization-Header, daher fetch + eigener Parser.
 */
export async function streamMessage(
  conversationId: string,
  content: string,
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const response = await authorizedFetch(`/api/conversations/${conversationId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ content }),
    signal,
  });
  if (!response.body) throw new Error('Keine Antwort vom Server');

  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  let finished = false;

  const dispatch = (raw: string) => {
    let event = 'message';
    const data: string[] = [];
    for (const line of raw.split('\n')) {
      if (line.startsWith(':')) continue;
      const idx = line.indexOf(':');
      const field = idx === -1 ? line : line.slice(0, idx);
      const value = idx === -1 ? '' : line.slice(idx + 1).replace(/^ /, '');
      if (field === 'event') event = value;
      else if (field === 'data') data.push(value);
    }
    if (data.length === 0) return;
    const payload = JSON.parse(data.join('\n'));
    if (event === 'token') handlers.onToken(payload.t);
    else if (event === 'done') {
      finished = true;
      handlers.onDone(payload as ChatMessage);
    } else if (event === 'error') {
      finished = true;
      handlers.onError(payload.message ?? 'Unbekannter Fehler');
    }
  };

  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += value.replace(/\r\n?/g, '\n');
    let boundary;
    while ((boundary = buffer.indexOf('\n\n')) !== -1) {
      dispatch(buffer.slice(0, boundary));
      buffer = buffer.slice(boundary + 2);
    }
  }
  if (buffer.trim()) dispatch(buffer);
  if (!finished) handlers.onError('Verbindung vorzeitig beendet');
}
