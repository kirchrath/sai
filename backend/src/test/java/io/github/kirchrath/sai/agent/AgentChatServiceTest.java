package io.github.kirchrath.sai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import io.github.kirchrath.sai.TestAgents;
import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.knowledge.KnowledgeService;
import io.github.kirchrath.sai.task.TaskListener;
import io.github.kirchrath.sai.task.TaskService;
import io.github.kirchrath.sai.tools.CalculatorTools;
import io.github.kirchrath.sai.tools.DateTimeTools;
import io.github.kirchrath.sai.tools.KnowledgeTools;
import io.github.kirchrath.sai.tools.TaskTools;
import io.github.kirchrath.sai.tools.ToolContextKeys;
import io.github.kirchrath.sai.tools.WebFetchTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;

class AgentChatServiceTest {

    private final ChatModel model = mock(ChatModel.class);
    private final ChatModelResolver resolver = mock(ChatModelResolver.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final SessionService sessions = DefaultSessionService.builder()
            .sessionRepository(InMemorySessionRepository.builder().build()).build();
    private final TaskListener listener = task -> { };
    private AgentChatService service;

    @BeforeEach
    void setUp() {
        SaiProperties properties = new SaiProperties(null, new SaiProperties.Chat(10, null),
                new SaiProperties.Knowledge(100, 3, 0.5), null, null);
        KnowledgeService knowledge = new KnowledgeService(vectorStore, null, null, properties);
        service = new AgentChatService(resolver, sessions, vectorStore, knowledge, properties, new DateTimeTools(),
                new CalculatorTools(), mock(WebFetchTools.class), new KnowledgeTools(knowledge),
                new TaskTools(mock(TaskService.class)));
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

    private static String systemText(Prompt prompt) {
        return prompt.getInstructions().stream().filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .map(Message::getText).findFirst().orElseThrow();
    }

    @Test
    void streamsWithFullConfiguration() {
        AgentDefinition agent = new AgentDefinition("voll", "Voll", "", AgentDefinition.Provider.OPENAI, "gpt-x", 0.3,
                500, "Sei hilfreich.", Set.of(AgentTool.CALCULATOR, AgentTool.DATETIME, AgentTool.TASKS), true);

        List<String> chunks = service.stream(new ChatTurn(agent, "c-1", "alice", "Hallo?", listener))
                .collectList().block();

        assertThat(chunks).containsExactly("Hal", "lo");
        verify(resolver).resolve(AgentDefinition.Provider.OPENAI);
        Prompt prompt = capturedPrompt();
        assertThat(prompt.getOptions().getModel()).isEqualTo("gpt-x");
        assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.3);
        assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(500);
        assertThat(systemText(prompt)).startsWith("Sei hilfreich.").endsWith(AgentChatService.FORMAT_INSTRUCTIONS);
        // Automatisches RAG haengt den Kontext an die Benutzernachricht an
        assertThat(prompt.getUserMessage().getText()).contains("Hallo?").contains("Wartung ist dienstags");
        ToolCallingChatOptions toolOptions = (ToolCallingChatOptions) prompt.getOptions();
        assertThat(toolOptions.getToolCallbacks()).extracting(t -> t.getToolDefinition().name())
                .containsExactlyInAnyOrder("calculate", "current_datetime", "create_task");
        assertThat(toolOptions.getToolContext())
                .containsEntry(ToolContextKeys.AGENT_ID, "voll")
                .containsEntry(ToolContextKeys.USER_ID, "alice")
                .containsEntry(ToolContextKeys.CONVERSATION_ID, "c-1")
                .containsEntry(ToolContextKeys.TASK_LISTENER, listener);
        // Gespraechskontext landet in der Session der Unterhaltung
        assertThat(sessions.getMessages("c-1")).extracting(Message::getText).contains("Hallo?", "Hallo");
    }

    @Test
    void streamsWithMinimalConfiguration() {
        service.stream(new ChatTurn(TestAgents.plain("minimal"), "c-2", "bob", "Hi", listener)).blockLast();

        Prompt prompt = capturedPrompt();
        assertThat(prompt.getOptions().getModel()).isEqualTo("model-x");
        assertThat(prompt.getOptions().getTemperature()).isNull();
        assertThat(prompt.getOptions().getMaxTokens()).isNull();
        assertThat(systemText(prompt)).isEqualTo(AgentChatService.FORMAT_INSTRUCTIONS);
        assertThat(prompt.getUserMessage().getText()).isEqualTo("Hi");
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolOptions) {
            assertThat(toolOptions.getToolCallbacks()).isNullOrEmpty();
        }
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void memoryWindowLimitsHistory() {
        for (int i = 0; i < 8; i++) {
            service.stream(new ChatTurn(TestAgents.plain("a"), "c-3", "bob", "Frage " + i, listener)).blockLast();
        }

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, org.mockito.Mockito.times(8)).stream(prompts.capture());
        long history = prompts.getValue().getInstructions().stream()
                .filter(m -> m.getMessageType() != MessageType.SYSTEM).count();
        // Fenster von 10 vorherigen Nachrichten plus die aktuelle Frage
        assertThat(history).isEqualTo(11);
    }
}
