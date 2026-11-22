package io.github.kirchrath.sai;

import java.util.Set;

import io.github.kirchrath.sai.agent.AgentDefinition;
import io.github.kirchrath.sai.agent.AgentTool;

/** Hilfsmethoden fuer Agent-Definitionen in Tests. */
public final class TestAgents {

    private TestAgents() {
    }

    public static AgentDefinition agent(String id, Set<AgentTool> tools, boolean rag) {
        return new AgentDefinition(id, "Agent " + id, "", AgentDefinition.Provider.ANTHROPIC, "model-x", null, null,
                "", tools, rag);
    }

    public static AgentDefinition plain(String id) {
        return agent(id, Set.of(), false);
    }
}
