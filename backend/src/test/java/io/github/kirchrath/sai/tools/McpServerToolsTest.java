package io.github.kirchrath.sai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import io.github.kirchrath.sai.agent.AgentDefinition;
import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.knowledge.KnowledgeService;
import org.junit.jupiter.api.Test;

class McpServerToolsTest {

    private final KnowledgeService knowledge = mock(KnowledgeService.class);
    private final AgentRegistry registry = new AgentRegistry(new SaiProperties(null, null, null, null, List.of(
            new AgentDefinition("wissen", "Wissen", "Dokumente", AgentDefinition.Provider.ANTHROPIC, "m", null, null,
                    "", Set.of(), true),
            new AgentDefinition("plain", "Plain", "", AgentDefinition.Provider.OPENAI, "m", null, null, "", Set.of(),
                    false))));
    private final McpServerTools tools = new McpServerTools(registry, knowledge);

    @Test
    void listsAgents() {
        assertThat(tools.listAgents()).isEqualTo("- wissen: Wissen - Dokumente [Wissensbasis]\n- plain: Plain");
    }

    @Test
    void searchesKnowledgeOfKnownAgent() {
        when(knowledge.searchAsText("wissen", "Frage")).thenReturn("Antwort");

        assertThat(tools.searchAgentKnowledge("wissen", "Frage")).isEqualTo("Antwort");
    }

    @Test
    void rejectsUnknownAgent() {
        assertThat(tools.searchAgentKnowledge("gibtsnicht", "Frage")).isEqualTo("Unbekannter Agent: gibtsnicht");
        verifyNoInteractions(knowledge);
    }
}
