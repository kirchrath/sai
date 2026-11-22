package io.github.kirchrath.sai.conversation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Unterhaltung eines Benutzers (Keycloak-Subject) mit einem Agenten.
 */
@Table("conversation")
public record Conversation(
        @Id UUID id,
        @Version Long version,
        String userId,
        String agentId,
        String title,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant modifiedAt) {

    /** Neue, noch nicht gespeicherte Unterhaltung ({@code version == null} kennzeichnet sie als neu). */
    public static Conversation create(String userId, String agentId, String title) {
        return new Conversation(UUID.randomUUID(), null, userId, agentId, title, null, null);
    }

    public Conversation withTitle(String newTitle) {
        return new Conversation(id, version, userId, agentId, newTitle, createdAt, modifiedAt);
    }
}
