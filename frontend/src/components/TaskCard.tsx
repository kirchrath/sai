import { PRIORITY_COLORS, PRIORITY_LABELS, STATUS_COLORS, STATUS_LABELS, type Task } from '../lib/types';
import Markdown from './Markdown';

interface Props {
  task: Task;
  /** Optional: Schritt abhaken direkt in der Karte */
  onToggleStep?: (task: Task, stepId: string) => void;
  onEdit?: (task: Task) => void;
  onDelete?: (task: Task) => void;
  compact?: boolean;
}

export function formatDate(date: string | null): string | null {
  return date ? new Date(`${date}T00:00:00`).toLocaleDateString('de-DE') : null;
}

export default function TaskCard({ task, onToggleStep, onEdit, onDelete, compact }: Props) {
  const done = task.steps.filter((s) => s.done).length;
  const overdue = task.dueDate !== null && task.status !== 'DONE' && new Date(`${task.dueDate}T23:59:59`) < new Date();

  return (
    <div className="card task-card mb-3">
      <header className="card-header">
        <p className="card-header-title">
          <span className="tag is-primary is-light mr-2">Aufgabe</span>
          {task.title}
        </p>
      </header>
      <div className="card-content py-3">
        <div className="tags mb-2">
          <span className={`tag ${STATUS_COLORS[task.status]}`}>{STATUS_LABELS[task.status]}</span>
          <span className={`tag ${PRIORITY_COLORS[task.priority]}`}>Prioritaet: {PRIORITY_LABELS[task.priority]}</span>
          {task.dueDate && (
            <span className={`tag ${overdue ? 'is-danger' : 'is-light'}`}>Faellig: {formatDate(task.dueDate)}</span>
          )}
          {task.steps.length > 0 && (
            <span className="tag is-light">
              {done}/{task.steps.length} Schritte
            </span>
          )}
        </div>
        {!compact && task.description && <Markdown text={task.description} />}
        {task.steps.length > 0 && (
          <ul className="task-steps">
            {task.steps.map((step) => (
              <li key={step.id}>
                <label className="checkbox">
                  <input
                    type="checkbox"
                    className="mr-2"
                    checked={step.done}
                    disabled={!onToggleStep}
                    onChange={() => onToggleStep?.(task, step.id)}
                  />
                  <span className={step.done ? 'has-text-grey task-step-done' : ''}>{step.text}</span>
                </label>
              </li>
            ))}
          </ul>
        )}
      </div>
      {(onEdit || onDelete) && (
        <footer className="card-footer">
          {onEdit && (
            <a className="card-footer-item" onClick={() => onEdit(task)}>
              Bearbeiten
            </a>
          )}
          {onDelete && (
            <a className="card-footer-item has-text-danger" onClick={() => onDelete(task)}>
              Loeschen
            </a>
          )}
        </footer>
      )}
    </div>
  );
}
