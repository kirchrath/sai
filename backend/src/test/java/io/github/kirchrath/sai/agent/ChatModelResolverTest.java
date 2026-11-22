package io.github.kirchrath.sai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ChatModelResolverTest {

    @Test
    void resolvesConfiguredProviders() {
        AnthropicChatModel anthropic = mock(AnthropicChatModel.class);
        OpenAiChatModel openAi = mock(OpenAiChatModel.class);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("anthropic", anthropic);
        beans.addBean("openai", openAi);

        ChatModelResolver resolver = new ChatModelResolver(beans.getBeanProvider(AnthropicChatModel.class),
                beans.getBeanProvider(OpenAiChatModel.class));

        assertThat(resolver.resolve(AgentDefinition.Provider.ANTHROPIC)).isSameAs(anthropic);
        assertThat(resolver.resolve(AgentDefinition.Provider.OPENAI)).isSameAs(openAi);
    }

    @Test
    void missingProviderFails() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        ChatModelResolver resolver = new ChatModelResolver(beans.getBeanProvider(AnthropicChatModel.class),
                beans.getBeanProvider(OpenAiChatModel.class));

        assertThatThrownBy(() -> resolver.resolve(AgentDefinition.Provider.OPENAI))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OPENAI");
    }
}
