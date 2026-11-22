package io.github.kirchrath.sai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.knowledge.KnowledgeService;
import io.github.kirchrath.sai.tools.CalculatorTools;
import io.github.kirchrath.sai.tools.DateTimeTools;
import io.github.kirchrath.sai.tools.KnowledgeTools;
import io.github.kirchrath.sai.tools.WebFetchTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

class AgentChatServiceTest {

    private final ChatModel model = mock(ChatModel.class);
    private final ChatModelResolver resolver = mock(ChatModelResolver.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ChatMemory memory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository()).build();
    private AgentChatService service;

    @BeforeEach
    void setUp() {
        SaiProperties properties = new SaiProperties(null, null, new SaiProperties.Knowledge(100, 3, 0.5), null, null);
        KnowledgeService knowledge = new KnowledgeService(vectorStore, null, null, properties);
        service = new AgentChatService(resolver, memory, vectorStore, knowledge, new DateTimeTools(),
                new CalculatorTools(), mock(WebFetchTools.class), new KnowledgeTools(knowledge));
        when(resolver.resolve(any())).thenReturn(model);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response("Hal"), response("lo")));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Wartung ist dienstags")));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private Prompt capturedPrompt() {
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(prompt.capture());
        return prompt.getValue();
    }

    @Test
    void streamsWithFullConfiguration() {
        AgentDefinition agent = new AgentDefinition("voll", "Voll", "", AgentDefinition.Provider.OPENAI, "gpt-x", 0.3,
                500, "Sei hilfreich.", Set.of(AgentTool.CALCULATOR, AgentTool.DATETIME), true);

        List<String> chunks = service.stream(agent, "c-1", "Hallo?").collectList().block();

        assertThat(chunks).containsExactly("Hal", "lo");
        verify(resolver).resolve(AgentDefinition.Provider.OPENAI);
        Prompt prompt = capturedPrompt();
        assertThat(prompt.getOptions().getModel()).isEqualTo("gpt-x");
        assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.3);
        assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(500);
        assertThat(prompt.getInstructions()).anySatisfy(m -> {
            assertThat(m.getMessageType()).isEqualTo(MessageType.SYSTEM);
            assertThat(m.getText()).contains("Sei hilfreich.");
        });
        // Automatisches RAG haengt den Kontext an die Benutzernachricht an
        assertThat(prompt.getUserMessage().getText()).contains("Hallo?").contains("Wartung ist dienstags");
        ToolCallingChatOptions toolOptions = (ToolCallingChatOptions) prompt.getOptions();
        assertThat(toolOptions.getToolCallbacks()).extracting(t -> t.getToolDefinition().name())
                .containsExactlyInAnyOrder("calculate", "current_datetime");
        assertThat(toolOptions.getToolContext()).containsEntry(KnowledgeTools.AGENT_ID, "voll");
        // Verlauf landet in der ChatMemory der Unterhaltung
        assertThat(memory.get("c-1")).extracting(m -> m.getText()).contains("Hallo?", "Hallo");
    }

    @Test
    void streamsWithMinimalConfiguration() {
        AgentDefinition agent = io.github.kirchrath.sai.TestAgents.plain("minimal");

        service.stream(agent, "c-2", "Hi").blockLast();

        Prompt prompt = capturedPrompt();
        assertThat(prompt.getOptions().getModel()).isEqualTo("model-x");
        assertThat(prompt.getOptions().getTemperature()).isNull();
        assertThat(prompt.getOptions().getMaxTokens()).isNull();
        assertThat(prompt.getInstructions()).noneMatch(m -> m.getMessageType() == MessageType.SYSTEM);
        assertThat(prompt.getUserMessage().getText()).isEqualTo("Hi");
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolOptions) {
            assertThat(toolOptions.getToolCallbacks()).isNullOrEmpty();
        }
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }
}
