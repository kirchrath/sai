import { useCallback, useEffect, useState } from 'react';
import { api } from '../lib/api';
import { STATUS_LABELS, toUpdate, type Agent, type Task, type TaskStatus, type TaskUpdate } from '../lib/types';
import TaskCard from './TaskCard';
import TaskEditModal from './TaskEditModal';

type Filter = TaskStatus | 'ALL';

export default function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [filter, setFilter] = useState<Filter>('OPEN');
  const [editing, setEditing] = useState<Task | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTasks(await api.tasks(filter === 'ALL' ? {} : { status: filter }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    api.agents().then(setAgents).catch(() => {});
  }, []);

  const replace = (updated: Task) => {
    setTasks((list) =>
      list
        .map((t) => (t.id === updated.id ? updated : t))
        .filter((t) => filter === 'ALL' || t.status === filter),
    );
  };

  const save = async (task: Task, update: TaskUpdate) => {
    try {
      replace(await api.updateTask(task.id, update));
    } catch (e) {
      setError((e as Error).message);
      load();
    }
  };

  const toggleStep = (task: Task, stepId: string) => {
    const update = toUpdate(task);
    update.steps = update.steps.map((s) => (s.id === stepId ? { ...s, done: !s.done } : s));
    // Sofort anzeigen; die Serverantwort (neue Versionen) ersetzt den Stand danach
    replace({ ...task, steps: task.steps.map((s) => (s.id === stepId ? { ...s, done: !s.done } : s)) });
    save(task, update);
  };

  const remove = async (task: Task) => {
    if (!window.confirm(`Aufgabe "${task.title}" loeschen?`)) return;
    try {
      await api.deleteTask(task.id);
      setTasks((list) => list.filter((t) => t.id !== task.id));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const agentName = (id: string) => agents.find((a) => a.id === id)?.name ?? id;
  const filters: Filter[] = ['OPEN', 'IN_PROGRESS', 'DONE', 'ALL'];

  return (
    <section className="section scroll-y" style={{ height: '100%' }}>
      <div className="container">
        <h1 className="title">Aufgaben</h1>
        <p className="subtitle is-6">Von Agenten angelegte Aufgaben. Schritte abhaken, bearbeiten oder loeschen.</p>

        <div className="tabs">
          <ul>
            {filters.map((f) => (
              <li key={f} className={filter === f ? 'is-active' : ''}>
                <a onClick={() => setFilter(f)}>{f === 'ALL' ? 'Alle' : STATUS_LABELS[f]}</a>
              </li>
            ))}
          </ul>
        </div>

        {error && (
          <div className="notification is-danger is-light">
            <button className="delete" onClick={() => setError(null)} />
            {error}
          </div>
        )}
        {loading && <progress className="progress is-small is-primary" max={100} />}
        {!loading && tasks.length === 0 && <p className="has-text-grey">Keine Aufgaben in dieser Ansicht.</p>}

        <div className="columns is-multiline">
          {tasks.map((task) => (
            <div className="column is-half" key={task.id}>
              <TaskCard task={task} onToggleStep={toggleStep} onEdit={setEditing} onDelete={remove} />
              <p className="is-size-7 has-text-grey mt-n2 mb-3">
                {agentName(task.agentId)} · angelegt {new Date(task.createdAt).toLocaleString('de-DE')}
              </p>
            </div>
          ))}
        </div>
      </div>
      {editing && (
        <TaskEditModal
          task={editing}
          onClose={() => setEditing(null)}
          onSave={async (update) => {
            const updated = await api.updateTask(editing.id, update);
            replace(updated);
            setEditing(null);
          }}
        />
      )}
    </section>
  );
}
