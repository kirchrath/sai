package io.github.kirchrath.sai.agent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.web.NotFoundException;
import org.springframework.stereotype.Component;

/**
 * Haelt die konfigurierten Agenten.
 */
@Component
public class AgentRegistry {

    private final Map<String, AgentDefinition> agents = new LinkedHashMap<>();

    public AgentRegistry(SaiProperties properties) {
        for (AgentDefinition agent : properties.agents()) {
            if (agents.putIfAbsent(agent.id(), agent) != null) {
                throw new IllegalStateException("Agent-ID doppelt konfiguriert: " + agent.id());
            }
        }
    }

    public Collection<AgentDefinition> all() {
        return agents.values();
    }

    public Optional<AgentDefinition> find(String id) {
        return Optional.ofNullable(agents.get(id));
    }

    public AgentDefinition get(String id) {
        return find(id).orElseThrow(() -> new NotFoundException("Unbekannter Agent: " + id));
    }
}
