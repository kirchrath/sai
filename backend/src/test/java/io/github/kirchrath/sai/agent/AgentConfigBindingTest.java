package io.github.kirchrath.sai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kirchrath.sai.config.SaiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AgentConfigBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Props.class)
            .withBean(AgentRegistry.class)
            .withPropertyValues(
                    "sai.security.issuer-uri=http://issuer",
                    "sai.security.jwk-set-uri=http://jwks");

    @Test
    void bindsAgentsFromYamlStyleProperties() {
        runner.withPropertyValues(
                        "sai.agents[0].id=recherche",
                        "sai.agents[0].name=Recherche",
                        "sai.agents[0].provider=openai",
                        "sai.agents[0].model=some-model",
                        "sai.agents[0].temperature=0.2",
                        "sai.agents[0].tools=web-fetch,knowledge,datetime",
                        "sai.agents[0].rag=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AgentDefinition agent = context.getBean(AgentRegistry.class).get("recherche");
                    assertThat(agent.provider()).isEqualTo(AgentDefinition.Provider.OPENAI);
                    assertThat(agent.tools()).containsExactlyInAnyOrder(AgentTool.WEB_FETCH, AgentTool.KNOWLEDGE,
                            AgentTool.DATETIME);
                    assertThat(agent.temperature()).isEqualTo(0.2);
                    assertThat(agent.maxTokens()).isNull();
                    assertThat(agent.systemPrompt()).isEmpty();
                    assertThat(agent.usesKnowledge()).isTrue();
                });
    }

    @Test
    void rejectsInvalidAgentId() {
        runner.withPropertyValues(
                        "sai.agents[0].id=Ungueltig Mit Leerzeichen",
                        "sai.agents[0].name=X",
                        "sai.agents[0].provider=anthropic",
                        "sai.agents[0].model=m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsDuplicateIds() {
        runner.withPropertyValues(
                        "sai.agents[0].id=a", "sai.agents[0].name=A", "sai.agents[0].provider=anthropic",
                        "sai.agents[0].model=m",
                        "sai.agents[1].id=a", "sai.agents[1].name=B", "sai.agents[1].provider=openai",
                        "sai.agents[1].model=m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SaiProperties.class)
    static class Props {
    }
}
