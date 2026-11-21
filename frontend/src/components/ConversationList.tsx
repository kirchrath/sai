import { useState } from 'react';
import type { Agent, Conversation } from '../lib/types';

interface Props {
  conversations: Conversation[];
  agents: Agent[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  onRename: (id: string, title: string) => void;
}

export default function ConversationList({ conversations, agents, activeId, onSelect, onDelete, onRename }: Props) {
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const agentName = (id: string) => agents.find((a) => a.id === id)?.name ?? id;

  const commit = (id: string) => {
    const title = draft.trim();
    if (title) onRename(id, title);
    setEditing(null);
  };

  if (conversations.length === 0) {
    return <p className="has-text-grey is-size-7">Noch keine Unterhaltungen.</p>;
  }

  return (
    <aside className="menu">
      <p className="menu-label">Unterhaltungen</p>
      <ul className="menu-list">
        {conversations.map((c) => (
          <li key={c.id}>
            {editing === c.id ? (
              <div className="field has-addons p-1">
                <div className="control is-expanded">
                  <input
                    className="input is-small"
                    value={draft}
                    autoFocus
                    maxLength={200}
                    onChange={(e) => setDraft(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') commit(c.id);
                      if (e.key === 'Escape') setEditing(null);
                    }}
                  />
                </div>
                <div className="control">
                  <button className="button is-small is-primary" onClick={() => commit(c.id)}>
                    OK
                  </button>
                </div>
              </div>
            ) : (
              <a className={activeId === c.id ? 'is-active' : ''} onClick={() => onSelect(c.id)}>
                <div className="is-flex is-align-items-center">
                  <div className="is-flex-grow-1" style={{ minWidth: 0 }}>
                    <p className="conversation-title" title={c.title}>
                      {c.title}
                    </p>
                    <p className="is-size-7 has-text-grey">
                      {agentName(c.agentId)} · {new Date(c.updatedAt).toLocaleString('de-DE')}
                    </p>
                  </div>
                  <div className="buttons are-small ml-2 is-flex-shrink-0">
                    <button
                      className="button is-small is-white"
                      title="Umbenennen"
                      onClick={(e) => {
                        e.stopPropagation();
                        setDraft(c.title);
                        setEditing(c.id);
                      }}
                    >
                      ✎
                    </button>
                    <button
                      className="delete is-small"
                      title="Loeschen"
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(c.id);
                      }}
                    />
                  </div>
                </div>
              </a>
            )}
          </li>
        ))}
      </ul>
    </aside>
  );
}
