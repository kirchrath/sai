package io.github.kirchrath.sai.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import io.github.kirchrath.sai.agent.AgentDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Zentrale Anwendungskonfiguration unter dem Praefix {@code sai}.
 */
@Validated
@ConfigurationProperties("sai")
public record SaiProperties(
        @Valid Security security,
        @Valid @DefaultValue Chat chat,
        @Valid @DefaultValue Knowledge knowledge,
        @Valid @DefaultValue WebFetch webFetch,
        List<@Valid AgentDefinition> agents) {

    public SaiProperties {
        agents = agents == null ? List.of() : List.copyOf(agents);
    }

    /**
     * @param issuerUri  oeffentliche Issuer-URL, wie sie im {@code iss}-Claim der Tokens steht
     * @param jwkSetUri  intern erreichbare URL der JWK-Schluessel (z.B. ueber das Docker-Netz)
     * @param adminRole  Realm-Rolle, die Wissensdokumente verwalten darf
     */
    public record Security(
            @NotBlank String issuerUri,
            @NotBlank String jwkSetUri,
            @DefaultValue("admin") String adminRole) {
    }

    /**
     * @param memoryMaxMessages Groesse des Kontextfensters, das dem Modell aus der ChatMemory mitgegeben wird
     */
    public record Chat(@DefaultValue("30") int memoryMaxMessages) {
    }

    public record Knowledge(
            @DefaultValue("800") int chunkSize,
            @DefaultValue("5") int topK,
            @DefaultValue("0.4") double similarityThreshold) {
    }

    public record WebFetch(
            @DefaultValue("15s") Duration timeout,
            @DefaultValue("2000000") int maxBytes,
            @DefaultValue("20000") int maxChars,
            @DefaultValue("false") boolean allowPrivateNetworks,
            @DefaultValue Set<String> blockedHosts) {
    }
}
