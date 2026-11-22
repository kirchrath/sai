package io.github.kirchrath.sai.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.knowledge.KnowledgeService;
import io.github.kirchrath.sai.tools.CalculatorTools;
import io.github.kirchrath.sai.tools.DateTimeTools;
import io.github.kirchrath.sai.tools.KnowledgeTools;
import io.github.kirchrath.sai.tools.TaskTools;
import io.github.kirchrath.sai.tools.ToolContextKeys;
import io.github.kirchrath.sai.tools.WebFetchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.MemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Fuehrt einen Chat-Turn mit einem konfigurierten Agenten aus: Modell/Provider, System-Prompt,
 * Session-Memory, Tools und optional automatisches RAG.
 */
@Service
public class AgentChatService {

    /** Ergaenzt jeden System-Prompt um die Regeln fuer das Antwortformat. */
    static final String FORMAT_INSTRUCTIONS = """
            Antwortformat: Informationen beantwortest du in Markdown (Ueberschriften, Listen, Tabellen, Code). \
            Ist das Ergebnis eine Aufgabe, legst du sie mit dem Tool create_task an, statt sie nur zu beschreiben, \
            und bestaetigst danach kurz in Markdown.""";

    private final ChatModelResolver modelResolver;
    private final SessionService sessionService;
    private final VectorStore vectorStore;
    private final KnowledgeService knowledgeService;
    private final int memoryMaxMessages;
    private final Map<AgentTool, Object> toolBeans;

    public AgentChatService(ChatModelResolver modelResolver, SessionService sessionService, VectorStore vectorStore,
            KnowledgeService knowledgeService, SaiProperties properties, DateTimeTools dateTimeTools,
            CalculatorTools calculatorTools, WebFetchTools webFetchTools, KnowledgeTools knowledgeTools,
            TaskTools taskTools) {
        this.modelResolver = modelResolver;
        this.sessionService = sessionService;
        this.vectorStore = vectorStore;
        this.knowledgeService = knowledgeService;
        this.memoryMaxMessages = properties.chat().memoryMaxMessages();
        this.toolBeans = Map.of(
                AgentTool.DATETIME, dateTimeTools,
                AgentTool.CALCULATOR, calculatorTools,
                AgentTool.WEB_FETCH, webFetchTools,
                AgentTool.KNOWLEDGE, knowledgeTools,
                AgentTool.TASKS, taskTools);
    }

    public Flux<String> stream(ChatTurn turn) {
        AgentDefinition agent = turn.agent();
        ChatClient client = ChatClient.builder(modelResolver.resolve(agent.provider())).build();

        ChatOptions.Builder<?> options = ChatOptions.builder().model(agent.model());
        if (agent.temperature() != null) {
            options.temperature(agent.temperature());
        }
        if (agent.maxTokens() != null) {
            options.maxTokens(agent.maxTokens());
        }

        String systemPrompt = agent.systemPrompt().isBlank()
                ? FORMAT_INSTRUCTIONS
                : agent.systemPrompt().strip() + "\n\n" + FORMAT_INSTRUCTIONS;

        ChatClient.ChatClientRequestSpec request = client.prompt()
                .options(options)
                .system(systemPrompt)
                .user(turn.message())
                .advisors(SessionMemoryAdvisor.builder(sessionService)
                        .eventFilter(EventFilter.lastN(memoryMaxMessages))
                        .build())
                .advisors(a -> a.param(MemoryAdvisor.CONVERSATION_ID, turn.conversationId()));

        if (agent.rag()) {
            request.advisors(QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(knowledgeService.searchRequest(agent.id(), null))
                    .build());
        }

        List<Object> tools = new ArrayList<>();
        for (AgentTool tool : agent.tools()) {
            tools.add(toolBeans.get(tool));
        }
        if (!tools.isEmpty()) {
            Map<String, Object> context = new HashMap<>();
            context.put(ToolContextKeys.AGENT_ID, agent.id());
            context.put(ToolContextKeys.USER_ID, turn.userId());
            context.put(ToolContextKeys.CONVERSATION_ID, turn.conversationId());
            context.put(ToolContextKeys.TASK_LISTENER, turn.taskListener());
            request.tools(tools.toArray()).toolContext(context);
        }
        return request.stream().content();
    }
}
