import { useEffect, useRef, useState } from 'react';
import { api, streamMessage } from '../lib/api';
import type { Agent, ChatMessage, Conversation } from '../lib/types';
import AgentTags from './AgentTags';

interface Props {
  conversation: Conversation;
  agent?: Agent;
  onUpdated: () => void;
}

export default function ChatView({ conversation, agent, onUpdated }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [streaming, setStreaming] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    api
      .messages(conversation.id)
      .then(setMessages)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
    return () => abortRef.current?.abort();
  }, [conversation.id]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, streaming]);

  const send = async () => {
    const content = draft.trim();
    if (!content || streaming !== null) return;
    setDraft('');
    setError(null);
    const optimistic: ChatMessage = {
      id: `local-${Date.now()}`,
      conversationId: conversation.id,
      role: 'USER',
      content,
      createdAt: new Date().toISOString(),
    };
    setMessages((list) => [...list, optimistic]);
    setStreaming('');

    const controller = new AbortController();
    abortRef.current = controller;
    let answer = '';
    try {
      await streamMessage(
        conversation.id,
        content,
        {
          onToken: (text) => {
            answer += text;
            setStreaming(answer);
          },
          onDone: (message) => setMessages((list) => [...list, message]),
          onError: (message) => setError(message),
        },
        controller.signal,
      );
    } catch (e) {
      if ((e as Error).name !== 'AbortError') setError((e as Error).message);
    } finally {
      setStreaming(null);
      abortRef.current = null;
      // Server-Stand uebernehmen (u.a. Titel, gespeicherte Teilantworten)
      api.messages(conversation.id).then(setMessages).catch(() => {});
      onUpdated();
    }
  };

  const stop = () => abortRef.current?.abort();

  return (
    <>
      <div className="px-4 pt-4 pb-2" style={{ borderBottom: '1px solid var(--bulma-border)' }}>
        <p className="title is-5 mb-2 conversation-title">{conversation.title}</p>
        {agent ? <AgentTags agent={agent} /> : <span className="tag is-warning">Agent nicht mehr konfiguriert</span>}
      </div>

      <div className="chat-messages scroll-y px-4 py-3">
        {loading && <progress className="progress is-small is-primary" max={100} />}
        {!loading && messages.length === 0 && streaming === null && (
          <p className="has-text-grey has-text-centered mt-6">Stelle deine erste Frage an {agent?.name ?? 'den Agenten'}.</p>
        )}
        {messages.map((m) => (
          <Bubble key={m.id} role={m.role} content={m.content} />
        ))}
        {streaming !== null && (
          <Bubble role="ASSISTANT" content={streaming} pending />
        )}
        {error && (
          <div className="notification is-danger is-light">
            <button className="delete" onClick={() => setError(null)} />
            {error}
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <div className="chat-input p-4" style={{ borderTop: '1px solid var(--bulma-border)' }}>
        <div className="field has-addons">
          <div className="control is-expanded">
            <textarea
              className="textarea"
              rows={2}
              placeholder="Nachricht eingeben – Enter sendet, Shift+Enter fuer neue Zeile"
              value={draft}
              disabled={!agent}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  send();
                }
              }}
            />
          </div>
          <div className="control">
            {streaming !== null ? (
              <button className="button is-danger is-light" style={{ height: '100%' }} onClick={stop}>
                Stopp
              </button>
            ) : (
              <button
                className="button is-primary"
                style={{ height: '100%' }}
                disabled={!draft.trim() || !agent}
                onClick={send}
              >
                Senden
              </button>
            )}
          </div>
        </div>
      </div>
    </>
  );
}

function Bubble({ role, content, pending }: { role: ChatMessage['role']; content: string; pending?: boolean }) {
  const isUser = role === 'USER';
  return (
    <div className={`message chat-bubble mb-3 ${isUser ? 'is-user is-primary' : 'is-light'}`}>
      <div className="message-body py-3 px-4">
        <p className="is-size-7 has-text-weight-semibold mb-1">{isUser ? 'Du' : 'Agent'}</p>
        {content}
        {pending && (
          <span className="has-text-grey">{content ? ' ▍' : 'denkt nach …'}</span>
        )}
      </div>
    </div>
  );
}
