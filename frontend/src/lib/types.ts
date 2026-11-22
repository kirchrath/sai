export type Provider = 'ANTHROPIC' | 'OPENAI';
export type AgentTool = 'DATETIME' | 'CALCULATOR' | 'WEB_FETCH' | 'KNOWLEDGE' | 'TASKS';

export interface Agent {
  id: string;
  name: string;
  description: string;
  provider: Provider;
  model: string;
  tools: AgentTool[];
  rag: boolean;
  knowledge: boolean;
}

export interface Conversation {
  id: string;
  agentId: string;
  title: string;
  version: number;
  createdAt: string;
  modifiedAt: string;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  version: number;
  createdAt: string;
  modifiedAt: string;
}

export interface KnowledgeDocument {
  id: string;
  agentId: string;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  chunkCount: number;
  uploadedBy: string;
  version: number;
  createdAt: string;
  modifiedAt: string;
}

export const TOOL_LABELS: Record<AgentTool, string> = {
  DATETIME: 'Datum/Uhrzeit',
  CALCULATOR: 'Rechner',
  WEB_FETCH: 'Web-Abruf',
  KNOWLEDGE: 'Wissenssuche',
  TASKS: 'Aufgaben',
};

export const PROVIDER_LABELS: Record<Provider, string> = {
  ANTHROPIC: 'Anthropic',
  OPENAI: 'OpenAI',
};

export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface TaskStep {
  id: string;
  version: number;
  taskId: string;
  position: number;
  text: string;
  done: boolean;
  createdAt: string;
  modifiedAt: string;
}

export interface Task {
  id: string;
  version: number;
  agentId: string;
  conversationId: string | null;
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  steps: TaskStep[];
  createdAt: string;
  modifiedAt: string;
}

export interface TaskUpdate {
  version: number;
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  steps: { id: string | null; text: string; done: boolean }[];
}

export const STATUS_LABELS: Record<TaskStatus, string> = {
  OPEN: 'Offen',
  IN_PROGRESS: 'In Arbeit',
  DONE: 'Erledigt',
};

export const STATUS_COLORS: Record<TaskStatus, string> = {
  OPEN: 'is-info',
  IN_PROGRESS: 'is-warning',
  DONE: 'is-success',
};

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Niedrig',
  MEDIUM: 'Mittel',
  HIGH: 'Hoch',
};

export const PRIORITY_COLORS: Record<TaskPriority, string> = {
  LOW: 'is-light',
  MEDIUM: 'is-link is-light',
  HIGH: 'is-danger is-light',
};

/** Aufgabe in ein Update-Objekt ueberfuehren (Basis fuer Aenderungen). */
export function toUpdate(task: Task): TaskUpdate {
  return {
    version: task.version,
    title: task.title,
    description: task.description,
    status: task.status,
    priority: task.priority,
    dueDate: task.dueDate,
    steps: task.steps.map((s) => ({ id: s.id, text: s.text, done: s.done })),
  };
}
