package io.github.kirchrath.sai.agent;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Liefert das ChatModel des Providers, den ein Agent konfiguriert hat.
 */
@Component
public class ChatModelResolver {

    private final Map<AgentDefinition.Provider, ObjectProvider<? extends ChatModel>> models =
            new EnumMap<>(AgentDefinition.Provider.class);

    public ChatModelResolver(ObjectProvider<AnthropicChatModel> anthropic, ObjectProvider<OpenAiChatModel> openAi) {
        models.put(AgentDefinition.Provider.ANTHROPIC, anthropic);
        models.put(AgentDefinition.Provider.OPENAI, openAi);
    }

    public ChatModel resolve(AgentDefinition.Provider provider) {
        ChatModel model = models.get(provider).getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("Provider " + provider + " ist nicht konfiguriert");
        }
        return model;
    }
}
