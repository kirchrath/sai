import { useState } from 'react';
import type { Agent } from '../lib/types';
import AgentTags from './AgentTags';

interface Props {
  agents: Agent[];
  onCreate: (agentId: string) => Promise<void>;
  onClose: () => void;
}

export default function NewConversationModal({ agents, onCreate, onClose }: Props) {
  const [selected, setSelected] = useState(agents[0]?.id ?? '');
  const [busy, setBusy] = useState(false);
  const agent = agents.find((a) => a.id === selected);

  const submit = async () => {
    setBusy(true);
    try {
      await onCreate(selected);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal is-active">
      <div className="modal-background" onClick={onClose} />
      <div className="modal-card">
        <header className="modal-card-head">
          <p className="modal-card-title">Neue Unterhaltung</p>
          <button className="delete" aria-label="Schliessen" onClick={onClose} />
        </header>
        <section className="modal-card-body">
          <div className="field">
            <label className="label" htmlFor="agent-select">
              Agent
            </label>
            <div className="control">
              <div className="select is-fullwidth">
                <select id="agent-select" value={selected} onChange={(e) => setSelected(e.target.value)}>
                  {agents.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>
          {agent && (
            <div className="content">
              {agent.description && <p>{agent.description}</p>}
              <AgentTags agent={agent} />
            </div>
          )}
        </section>
        <footer className="modal-card-foot">
          <div className="buttons">
            <button
              className={`button is-primary ${busy ? 'is-loading' : ''}`}
              disabled={!selected}
              onClick={submit}
            >
              Starten
            </button>
            <button className="button" onClick={onClose}>
              Abbrechen
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}
