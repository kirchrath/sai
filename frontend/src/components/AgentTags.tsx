import { PROVIDER_LABELS, TOOL_LABELS, type Agent } from '../lib/types';

export default function AgentTags({ agent }: { agent: Agent }) {
  return (
    <div className="tags">
      <span className="tag is-info is-light">
        {PROVIDER_LABELS[agent.provider]} · {agent.model}
      </span>
      {agent.tools.map((tool) => (
        <span key={tool} className="tag">
          {TOOL_LABELS[tool]}
        </span>
      ))}
      {agent.rag && <span className="tag is-success is-light">Automatisches RAG</span>}
    </div>
  );
}
