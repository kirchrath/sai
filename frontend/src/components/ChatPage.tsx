import { useCallback, useEffect, useState } from 'react';
import { api } from '../lib/api';
import type { Agent, Conversation } from '../lib/types';
import ConversationList from './ConversationList';
import ChatView from './ChatView';
import NewConversationModal from './NewConversationModal';

export default function ChatPage() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [showNew, setShowNew] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setConversations(await api.conversations());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    Promise.all([api.agents(), api.conversations()])
      .then(([a, c]) => {
        setAgents(a);
        setConversations(c);
        if (c.length > 0) setActiveId(c[0].id);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  const create = async (agentId: string) => {
    try {
      const conversation = await api.createConversation(agentId);
      setConversations((list) => [conversation, ...list]);
      setActiveId(conversation.id);
      setShowNew(false);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const remove = async (id: string) => {
    if (!window.confirm('Unterhaltung wirklich loeschen?')) return;
    try {
      await api.deleteConversation(id);
      setConversations((list) => list.filter((c) => c.id !== id));
      if (activeId === id) setActiveId(null);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const rename = async (id: string, title: string) => {
    try {
      const updated = await api.renameConversation(id, title);
      setConversations((list) => list.map((c) => (c.id === id ? updated : c)));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const active = conversations.find((c) => c.id === activeId) ?? null;
  const activeAgent = active ? agents.find((a) => a.id === active.agentId) : undefined;

  return (
    <div className="columns is-gapless">
      <aside className="column is-3 has-background-white-ter scroll-y p-3">
        <button className="button is-primary is-fullwidth mb-4" onClick={() => setShowNew(true)} disabled={!agents.length}>
          Neue Unterhaltung
        </button>
        <ConversationList
          conversations={conversations}
          agents={agents}
          activeId={activeId}
          onSelect={setActiveId}
          onDelete={remove}
          onRename={rename}
        />
      </aside>
      <div className="column chat-column">
        {error && (
          <div className="notification is-danger is-light m-3 mb-0">
            <button className="delete" onClick={() => setError(null)} />
            {error}
          </div>
        )}
        {active ? (
          <ChatView key={active.id} conversation={active} agent={activeAgent} onUpdated={reload} />
        ) : (
          <section className="section has-text-centered">
            <p className="title is-4">Willkommen</p>
            <p className="subtitle is-6">Waehle eine Unterhaltung oder starte eine neue.</p>
          </section>
        )}
      </div>
      {showNew && <NewConversationModal agents={agents} onCreate={create} onClose={() => setShowNew(false)} />}
    </div>
  );
}
