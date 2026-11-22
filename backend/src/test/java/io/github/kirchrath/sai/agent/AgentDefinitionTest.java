package io.github.kirchrath.sai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import io.github.kirchrath.sai.TestAgents;
import org.junit.jupiter.api.Test;

class AgentDefinitionTest {

    @Test
    void nullToolsBecomeEmptySet() {
        AgentDefinition agent = new AgentDefinition("a", "A", "", AgentDefinition.Provider.OPENAI, "m", null, null, "",
                null, false);

        assertThat(agent.tools()).isEmpty();
        assertThat(agent.usesKnowledge()).isFalse();
    }

    @Test
    void usesKnowledgeWithRagOrKnowledgeTool() {
        assertThat(TestAgents.agent("a", Set.of(), true).usesKnowledge()).isTrue();
        assertThat(TestAgents.agent("a", Set.of(AgentTool.KNOWLEDGE), false).usesKnowledge()).isTrue();
        assertThat(TestAgents.agent("a", Set.of(AgentTool.DATETIME), false).usesKnowledge()).isFalse();
    }

    @Test
    void toolsAreImmutableCopy() {
        var tools = new java.util.HashSet<>(Set.of(AgentTool.CALCULATOR));
        AgentDefinition agent = TestAgents.agent("a", tools, false);
        tools.add(AgentTool.WEB_FETCH);

        assertThat(agent.tools()).containsExactly(AgentTool.CALCULATOR);
    }
}
