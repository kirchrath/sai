package io.github.kirchrath.sai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import io.github.kirchrath.sai.TestAgents;
import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.web.NotFoundException;
import org.junit.jupiter.api.Test;

class AgentRegistryTest {

    static SaiProperties properties(List<AgentDefinition> agents) {
        return new SaiProperties(null, null, null, null, agents);
    }

    @Test
    void keepsConfiguredOrderAndFindsAgents() {
        AgentRegistry registry = new AgentRegistry(properties(List.of(TestAgents.plain("b"), TestAgents.plain("a"))));

        assertThat(registry.all()).extracting(AgentDefinition::id).containsExactly("b", "a");
        assertThat(registry.find("a")).isPresent();
        assertThat(registry.find("x")).isEmpty();
        assertThat(registry.get("b").id()).isEqualTo("b");
    }

    @Test
    void unknownAgentThrowsNotFound() {
        AgentRegistry registry = new AgentRegistry(properties(null));

        assertThatThrownBy(() -> registry.get("x")).isInstanceOf(NotFoundException.class).hasMessageContaining("x");
    }

    @Test
    void duplicateIdsAreRejected() {
        assertThatThrownBy(() -> new AgentRegistry(properties(List.of(TestAgents.plain("a"), TestAgents.plain("a")))))
                .isInstanceOf(IllegalStateException.class);
    }
}
