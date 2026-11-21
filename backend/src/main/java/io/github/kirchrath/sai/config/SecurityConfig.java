package io.github.kirchrath.sai.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Backend als OAuth2 Resource Server fuer Keycloak-Tokens. Gilt fuer die REST-API und den MCP-Endpoint.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SaiProperties properties) throws Exception {
        String admin = properties.security().adminRole();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Async-Dispatches (SSE) und Fehlerseiten gehoeren zu bereits autorisierten Requests
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/agents/*/documents").hasRole(admin)
                        .requestMatchers(HttpMethod.DELETE, "/api/agents/*/documents/*").hasRole(admin)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter())));
        return http.build();
    }

    /**
     * Schluessel werden intern geladen, der Issuer wird gegen die oeffentliche URL geprueft.
     */
    @Bean
    JwtDecoder jwtDecoder(SaiProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.security().jwkSetUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.security().issuerUri()));
        return decoder;
    }

    /** Uebernimmt Keycloak-Realm-Rollen ({@code realm_access.roles}) als {@code ROLE_<rolle>}. */
    static final class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            Collection<GrantedAuthority> authorities = realmRoles(jwt).stream()
                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            String name = jwt.getClaimAsString("preferred_username");
            return new JwtAuthenticationToken(jwt, authorities, name != null ? name : jwt.getSubject());
        }

        static List<String> realmRoles(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream().map(String::valueOf).toList();
        }
    }
}
