package io.github.kirchrath.sai.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocument(
        UUID id,
        String agentId,
        String filename,
        String contentType,
        long sizeBytes,
        int chunkCount,
        String uploadedBy,
        Instant createdAt) {
}
