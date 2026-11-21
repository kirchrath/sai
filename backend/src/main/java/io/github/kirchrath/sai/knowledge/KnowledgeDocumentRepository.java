package io.github.kirchrath.sai.knowledge;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeDocumentRepository {

    private final JdbcClient jdbc;

    public KnowledgeDocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(KnowledgeDocument d) {
        jdbc.sql("""
                INSERT INTO knowledge_document
                    (id, agent_id, filename, content_type, size_bytes, chunk_count, uploaded_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(d.id(), d.agentId(), d.filename(), d.contentType(), d.sizeBytes(), d.chunkCount(),
                        d.uploadedBy(), Timestamp.from(d.createdAt()))
                .update();
    }

    public List<KnowledgeDocument> findByAgent(String agentId) {
        return jdbc.sql("SELECT * FROM knowledge_document WHERE agent_id = ? ORDER BY created_at DESC")
                .param(agentId)
                .query(KnowledgeDocument.class)
                .list();
    }

    public Optional<KnowledgeDocument> find(String agentId, UUID id) {
        return jdbc.sql("SELECT * FROM knowledge_document WHERE agent_id = ? AND id = ?")
                .params(agentId, id)
                .query(KnowledgeDocument.class)
                .optional();
    }

    public void delete(UUID id) {
        jdbc.sql("DELETE FROM knowledge_document WHERE id = ?").param(id).update();
    }
}
