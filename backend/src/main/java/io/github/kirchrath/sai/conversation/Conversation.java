package io.github.kirchrath.sai.conversation;

import java.time.Instant;
import java.util.UUID;

public record Conversation(UUID id, String userId, String agentId, String title, Instant createdAt, Instant updatedAt) {
}
