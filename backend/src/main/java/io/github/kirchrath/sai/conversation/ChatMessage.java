package io.github.kirchrath.sai.conversation;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(UUID id, UUID conversationId, Role role, String content, Instant createdAt) {

    public enum Role {
        USER, ASSISTANT
    }
}
