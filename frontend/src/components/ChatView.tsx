import { useEffect, useRef, useState } from 'react';
import { api, streamMessage } from '../lib/api';
import { toUpdate, type Agent, type ChatMessage, type Conversation, type Task, type TaskUpdate } from '../lib/types';
import AgentTags from './AgentTags';
import Markdown from './Markdown';
import TaskCard from './TaskCard';
import TaskEditModal from './TaskEditModal';

type TimelineEntry = { kind: 'message'; at: string; message: ChatMessage } | { kind: 'task'; at: string; task: Task };

interface Props {
  conversation: Conversation;
  agent?: Agent;
  onUpdated: () => void;
}

export default function ChatView({ conversation, agent, onUpdated }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [editing, setEditing] = useState<Task | null>(null);
  const [draft, setDraft] = useState('');
  const [streaming, setStreaming] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    Promise.all([api.messages(conversation.id), api.tasks({ conversationId: conversation.id })])
      .then(([m, t]) => {
        setMessages(m);
        setTasks(t);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
    return () => abortRef.current?.abort();
  }, [conversation.id]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, tasks, streaming]);

  const replaceTask = (updated: Task) => setTasks((list) => list.map((t) => (t.id === updated.id ? updated : t)));

  const saveTask = async (task: Task, update: TaskUpdate) => {
    try {
      replaceTask(await api.updateTask(task.id, update));
    } catch (e) {
      replaceTask(task);
      setError((e as Error).message);
    }
  };

  const toggleStep = (task: Task, stepId: string) => {
    const update = toUpdate(task);
    update.steps = update.steps.map((s) => (s.id === stepId ? { ...s, done: !s.done } : s));
    // Sofort anzeigen; die Serverantwort (neue Versionen) ersetzt den Stand danach
    replaceTask({ ...task, steps: task.steps.map((s) => (s.id === stepId ? { ...s, done: !s.done } : s)) });
    saveTask(task, update);
  };

  const timeline: TimelineEntry[] = [
    ...messages.map((message): TimelineEntry => ({ kind: 'message', at: message.createdAt, message })),
    ...tasks.map((task): TimelineEntry => ({ kind: 'task', at: task.createdAt, task })),
  ].sort((a, b) => a.at.localeCompare(b.at));

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
      version: 0,
      createdAt: new Date().toISOString(),
      modifiedAt: new Date().toISOString(),
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
          onTask: (task) => setTasks((list) => [...list, task]),
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
        {timeline.map((entry) =>
          entry.kind === 'message' ? (
            <Bubble key={entry.message.id} role={entry.message.role} content={entry.message.content} />
          ) : (
            <div key={entry.task.id} className="chat-bubble">
              <TaskCard task={entry.task} onToggleStep={toggleStep} onEdit={setEditing} />
            </div>
          ),
        )}
        {streaming !== null && <Bubble role="ASSISTANT" content={streaming} pending />}
        {error && (
          <div className="notification is-danger is-light">
            <button className="delete" onClick={() => setError(null)} />
            {error}
          </div>
        )}
        <div ref={bottomRef} />
      </div>
      {editing && (
        <TaskEditModal
          task={editing}
          onClose={() => setEditing(null)}
          onSave={async (update) => {
            replaceTask(await api.updateTask(editing.id, update));
            setEditing(null);
          }}
        />
      )}

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
        {isUser ? <div className="user-text">{content}</div> : content && <Markdown text={content} />}
        {pending && <span className="has-text-grey">{content ? '▍' : 'denkt nach …'}</span>}
      </div>
    </div>
  );
}
