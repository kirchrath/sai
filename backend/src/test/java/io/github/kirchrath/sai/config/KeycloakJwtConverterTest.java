package io.github.kirchrath.sai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtConverterTest {

    private final SecurityConfig.KeycloakJwtConverter converter = new SecurityConfig.KeycloakJwtConverter();

    @Test
    void mapsRealmRolesAndUsername() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("admin", "user")),
                "preferred_username", "alice"));

        var token = converter.convert(jwt);

        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_admin", "ROLE_user");
        assertThat(token.getName()).isEqualTo("alice");
    }

    @Test
    void handlesMissingRoles() {
        var token = converter.convert(jwt(Map.of()));

        assertThat(token.getAuthorities()).isEmpty();
        assertThat(token.getName()).isEqualTo("subject-1");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
