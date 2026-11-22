import { useState } from 'react';
import {
  PRIORITY_LABELS,
  STATUS_LABELS,
  toUpdate,
  type Task,
  type TaskPriority,
  type TaskStatus,
  type TaskUpdate,
} from '../lib/types';

interface Props {
  task: Task;
  onSave: (update: TaskUpdate) => Promise<void>;
  onClose: () => void;
}

export default function TaskEditModal({ task, onSave, onClose }: Props) {
  const [form, setForm] = useState<TaskUpdate>(() => toUpdate(task));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = <K extends keyof TaskUpdate>(key: K, value: TaskUpdate[K]) => setForm((f) => ({ ...f, [key]: value }));
  const setStep = (index: number, patch: Partial<TaskUpdate['steps'][number]>) =>
    set('steps', form.steps.map((s, i) => (i === index ? { ...s, ...patch } : s)));
  const moveStep = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= form.steps.length) return;
    const steps = [...form.steps];
    [steps[index], steps[target]] = [steps[target], steps[index]];
    set('steps', steps);
  };

  const valid = form.title.trim() !== '' && form.steps.every((s) => s.text.trim() !== '');

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await onSave(form);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal is-active">
      <div className="modal-background" onClick={onClose} />
      <div className="modal-card" style={{ width: 'min(720px, 95vw)' }}>
        <header className="modal-card-head">
          <p className="modal-card-title">Aufgabe bearbeiten</p>
          <button className="delete" aria-label="Schliessen" onClick={onClose} />
        </header>
        <section className="modal-card-body">
          {error && <div className="notification is-danger is-light">{error}</div>}
          <div className="field">
            <label className="label" htmlFor="task-title">
              Titel
            </label>
            <div className="control">
              <input
                id="task-title"
                className="input"
                maxLength={200}
                value={form.title}
                onChange={(e) => set('title', e.target.value)}
              />
            </div>
          </div>
          <div className="field">
            <label className="label" htmlFor="task-description">
              Beschreibung (Markdown)
            </label>
            <div className="control">
              <textarea
                id="task-description"
                className="textarea"
                rows={4}
                value={form.description}
                onChange={(e) => set('description', e.target.value)}
              />
            </div>
          </div>
          <div className="columns">
            <div className="column field">
              <label className="label" htmlFor="task-status">
                Status
              </label>
              <div className="select is-fullwidth">
                <select
                  id="task-status"
                  value={form.status}
                  onChange={(e) => set('status', e.target.value as TaskStatus)}
                >
                  {Object.entries(STATUS_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="column field">
              <label className="label" htmlFor="task-priority">
                Prioritaet
              </label>
              <div className="select is-fullwidth">
                <select
                  id="task-priority"
                  value={form.priority}
                  onChange={(e) => set('priority', e.target.value as TaskPriority)}
                >
                  {Object.entries(PRIORITY_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="column field">
              <label className="label" htmlFor="task-due">
                Faellig am
              </label>
              <input
                id="task-due"
                className="input"
                type="date"
                value={form.dueDate ?? ''}
                onChange={(e) => set('dueDate', e.target.value || null)}
              />
            </div>
          </div>

          <label className="label">Checkliste</label>
          {form.steps.map((step, index) => (
            <div className="field has-addons" key={step.id ?? `neu-${index}`}>
              <div className="control">
                <label className="button is-static">
                  <input
                    type="checkbox"
                    aria-label="Erledigt"
                    checked={step.done}
                    onChange={(e) => setStep(index, { done: e.target.checked })}
                  />
                </label>
              </div>
              <div className="control is-expanded">
                <input
                  className="input"
                  maxLength={500}
                  value={step.text}
                  onChange={(e) => setStep(index, { text: e.target.value })}
                />
              </div>
              <div className="control">
                <button className="button" title="Nach oben" onClick={() => moveStep(index, -1)} disabled={index === 0}>
                  ↑
                </button>
              </div>
              <div className="control">
                <button
                  className="button"
                  title="Nach unten"
                  onClick={() => moveStep(index, 1)}
                  disabled={index === form.steps.length - 1}
                >
                  ↓
                </button>
              </div>
              <div className="control">
                <button
                  className="button is-danger is-light"
                  title="Schritt entfernen"
                  onClick={() => set('steps', form.steps.filter((_, i) => i !== index))}
                >
                  ✕
                </button>
              </div>
            </div>
          ))}
          <button
            className="button is-small"
            disabled={form.steps.length >= 100}
            onClick={() => set('steps', [...form.steps, { id: null, text: '', done: false }])}
          >
            Schritt hinzufuegen
          </button>
        </section>
        <footer className="modal-card-foot">
          <div className="buttons">
            <button className={`button is-primary ${busy ? 'is-loading' : ''}`} disabled={!valid} onClick={submit}>
              Speichern
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
