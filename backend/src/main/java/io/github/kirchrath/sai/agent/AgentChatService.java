package io.github.kirchrath.sai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.kirchrath.sai.knowledge.KnowledgeService;
import io.github.kirchrath.sai.tools.CalculatorTools;
import io.github.kirchrath.sai.tools.DateTimeTools;
import io.github.kirchrath.sai.tools.KnowledgeTools;
import io.github.kirchrath.sai.tools.WebFetchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Fuehrt einen Chat-Turn mit einem konfigurierten Agenten aus: Modell/Provider, System-Prompt,
 * ChatMemory, Tools und optional automatisches RAG.
 */
@Service
public class AgentChatService {

    private final ChatModelResolver modelResolver;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final KnowledgeService knowledgeService;
    private final Map<AgentTool, Object> toolBeans;

    public AgentChatService(ChatModelResolver modelResolver, ChatMemory chatMemory, VectorStore vectorStore,
            KnowledgeService knowledgeService, DateTimeTools dateTimeTools, CalculatorTools calculatorTools,
            WebFetchTools webFetchTools, KnowledgeTools knowledgeTools) {
        this.modelResolver = modelResolver;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.knowledgeService = knowledgeService;
        this.toolBeans = Map.of(
                AgentTool.DATETIME, dateTimeTools,
                AgentTool.CALCULATOR, calculatorTools,
                AgentTool.WEB_FETCH, webFetchTools,
                AgentTool.KNOWLEDGE, knowledgeTools);
    }

    public Flux<String> stream(AgentDefinition agent, String conversationId, String userMessage) {
        ChatClient client = ChatClient.builder(modelResolver.resolve(agent.provider())).build();

        ChatOptions.Builder<?> options = ChatOptions.builder().model(agent.model());
        if (agent.temperature() != null) {
            options.temperature(agent.temperature());
        }
        if (agent.maxTokens() != null) {
            options.maxTokens(agent.maxTokens());
        }

        ChatClient.ChatClientRequestSpec request = client.prompt()
                .options(options)
                .user(userMessage)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        if (!agent.systemPrompt().isBlank()) {
            request.system(agent.systemPrompt());
        }
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
            request.tools(tools.toArray())
                    .toolContext(Map.of(KnowledgeTools.AGENT_ID, agent.id()));
        }
        return request.stream().content();
    }
}
