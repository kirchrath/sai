package io.github.kirchrath.sai.agent;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRegistry registry;

    public AgentController(AgentRegistry registry) {
        this.registry = registry;
    }

    /** Oeffentliche Sicht auf einen Agenten (ohne System-Prompt). */
    public record AgentView(String id, String name, String description, AgentDefinition.Provider provider,
            String model, Set<AgentTool> tools, boolean rag, boolean knowledge) {

        static AgentView of(AgentDefinition a) {
            return new AgentView(a.id(), a.name(), a.description(), a.provider(), a.model(), a.tools(), a.rag(),
                    a.usesKnowledge());
        }
    }

    @GetMapping
    List<AgentView> list() {
        return registry.all().stream().map(AgentView::of).toList();
    }
}
