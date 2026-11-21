package io.github.kirchrath.sai.agent;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Konfiguration eines Agenten (aus YAML).
 *
 * @param id           technische ID, nur Kleinbuchstaben, Ziffern und Bindestrich
 * @param name         Anzeigename
 * @param description  Kurzbeschreibung fuer das UI
 * @param provider     LLM-Provider
 * @param model        Modellname beim Provider
 * @param temperature  optionale Temperatur
 * @param maxTokens    optionale maximale Antwortlaenge
 * @param systemPrompt System-Prompt
 * @param tools        aktivierte eingebaute Tools
 * @param rag          wenn {@code true}, wird automatisch Kontext aus der Wissensbasis des Agenten beigefuegt
 */
public record AgentDefinition(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String id,
        @NotBlank String name,
        @DefaultValue("") String description,
        @NotNull Provider provider,
        @NotBlank String model,
        Double temperature,
        Integer maxTokens,
        @DefaultValue("") String systemPrompt,
        @DefaultValue Set<AgentTool> tools,
        @DefaultValue("false") boolean rag) {

    public AgentDefinition {
        tools = tools == null ? Set.of() : Set.copyOf(tools);
    }

    /** Hat der Agent eine Wissensbasis (automatisch oder als Tool)? */
    public boolean usesKnowledge() {
        return rag || tools.contains(AgentTool.KNOWLEDGE);
    }

    public enum Provider {
        ANTHROPIC, OPENAI
    }
}
