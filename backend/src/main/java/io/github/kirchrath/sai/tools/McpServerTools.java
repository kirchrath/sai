package io.github.kirchrath.sai.tools;

import java.util.stream.Collectors;

import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.knowledge.KnowledgeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools, die nur ueber den MCP-Server angeboten werden. Externe Clients muessen den Agenten explizit angeben.
 */
@Component
public class McpServerTools {

    private final AgentRegistry agentRegistry;
    private final KnowledgeService knowledgeService;

    public McpServerTools(AgentRegistry agentRegistry, KnowledgeService knowledgeService) {
        this.agentRegistry = agentRegistry;
        this.knowledgeService = knowledgeService;
    }

    @Tool(name = "list_agents", description = "Listet die konfigurierten Agenten mit ID, Name und Beschreibung.")
    public String listAgents() {
        return agentRegistry.all().stream()
                .map(a -> "- %s: %s%s%s".formatted(a.id(), a.name(),
                        a.description().isBlank() ? "" : " - " + a.description(),
                        a.usesKnowledge() ? " [Wissensbasis]" : ""))
                .collect(Collectors.joining("\n"));
    }

    @Tool(name = "search_agent_knowledge",
            description = "Durchsucht die Wissensbasis eines Agenten semantisch. Agent-IDs liefert list_agents.")
    public String searchAgentKnowledge(@ToolParam(description = "ID des Agenten") String agentId,
            @ToolParam(description = "Suchanfrage in natuerlicher Sprache") String query) {
        if (agentRegistry.find(agentId).isEmpty()) {
            return "Unbekannter Agent: " + agentId;
        }
        return knowledgeService.searchAsText(agentId, query);
    }
}
