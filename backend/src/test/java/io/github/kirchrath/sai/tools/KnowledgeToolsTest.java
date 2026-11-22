package io.github.kirchrath.sai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import io.github.kirchrath.sai.knowledge.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class KnowledgeToolsTest {

    private final KnowledgeService knowledge = mock(KnowledgeService.class);
    private final KnowledgeTools tools = new KnowledgeTools(knowledge);

    @Test
    void searchesKnowledgeOfAgentFromContext() {
        when(knowledge.searchAsText("wissen", "Wartung")).thenReturn("Treffer");

        assertThat(tools.searchKnowledge("Wartung", new ToolContext(Map.of(KnowledgeTools.AGENT_ID, "wissen"))))
                .isEqualTo("Treffer");
    }

    @Test
    void withoutAgentInContextNothingIsSearched() {
        assertThat(tools.searchKnowledge("Wartung", new ToolContext(Map.of()))).isEqualTo("Keine Wissensbasis verfuegbar");
        verifyNoInteractions(knowledge);
    }
}
