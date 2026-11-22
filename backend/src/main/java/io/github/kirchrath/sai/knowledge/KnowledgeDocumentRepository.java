package io.github.kirchrath.sai.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

public interface KnowledgeDocumentRepository extends ListCrudRepository<KnowledgeDocument, UUID> {

    List<KnowledgeDocument> findByAgentIdOrderByCreatedAtDesc(String agentId);

    Optional<KnowledgeDocument> findByIdAndAgentId(UUID id, String agentId);
}
