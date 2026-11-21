package io.github.kirchrath.sai.tools;

import io.github.kirchrath.sai.knowledge.KnowledgeService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Wissenssuche fuer Agenten im Chat. Der Agent ergibt sich aus dem ToolContext,
 * das Modell kann also nur in der eigenen Wissensbasis suchen.
 */
@Component
public class KnowledgeTools {

    public static final String AGENT_ID = "agentId";

    private final KnowledgeService knowledgeService;

    public KnowledgeTools(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Tool(name = "search_knowledge",
            description = "Durchsucht die Wissensbasis (hochgeladene Dokumente) dieses Agenten semantisch.")
    public String searchKnowledge(@ToolParam(description = "Suchanfrage in natuerlicher Sprache") String query,
            ToolContext toolContext) {
        Object agentId = toolContext.getContext().get(AGENT_ID);
        if (agentId == null) {
            return "Keine Wissensbasis verfuegbar";
        }
        return knowledgeService.searchAsText(agentId.toString(), query);
    }
}
