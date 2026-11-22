package io.github.kirchrath.sai.conversation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Nachricht im anzeigbaren Verlauf einer Unterhaltung.
 */
@Table("chat_message")
public record ChatMessage(
        @Id UUID id,
        @Version Long version,
        UUID conversationId,
        Role role,
        String content,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant modifiedAt) {

    public static ChatMessage create(UUID conversationId, Role role, String content) {
        return new ChatMessage(UUID.randomUUID(), null, conversationId, role, content, null, null);
    }

    public enum Role {
        USER, ASSISTANT
    }
}
