package io.github.kirchrath.sai.knowledge;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Metadaten eines Dokuments in der Wissensbasis eines Agenten. Die Inhalte liegen als Chunks im VectorStore.
 */
@Table("knowledge_document")
public record KnowledgeDocument(
        @Id UUID id,
        @Version Long version,
        String agentId,
        String filename,
        String contentType,
        long sizeBytes,
        int chunkCount,
        String uploadedBy,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant modifiedAt) {

    public static KnowledgeDocument create(UUID id, String agentId, String filename, String contentType,
            long sizeBytes, int chunkCount, String uploadedBy) {
        return new KnowledgeDocument(id, null, agentId, filename, contentType, sizeBytes, chunkCount, uploadedBy,
                null, null);
    }
}
