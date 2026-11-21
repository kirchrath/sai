export type Provider = 'ANTHROPIC' | 'OPENAI';
export type AgentTool = 'DATETIME' | 'CALCULATOR' | 'WEB_FETCH' | 'KNOWLEDGE';

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
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string;
}

export interface KnowledgeDocument {
  id: string;
  agentId: string;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  chunkCount: number;
  uploadedBy: string;
  createdAt: string;
}

export const TOOL_LABELS: Record<AgentTool, string> = {
  DATETIME: 'Datum/Uhrzeit',
  CALCULATOR: 'Rechner',
  WEB_FETCH: 'Web-Abruf',
  KNOWLEDGE: 'Wissenssuche',
};

export const PROVIDER_LABELS: Record<Provider, string> = {
  ANTHROPIC: 'Anthropic',
  OPENAI: 'OpenAI',
};
