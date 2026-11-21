package io.github.kirchrath.sai.knowledge;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.kirchrath.sai.agent.AgentDefinition;
import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.web.NotFoundException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Verwaltet die Wissensbasis (RAG) pro Agent: Ingest via Tika, Chunking, Embeddings in pgvector.
 */
@Service
public class KnowledgeService {

    public static final String META_AGENT_ID = "agentId";
    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_FILENAME = "filename";

    private final VectorStore vectorStore;
    private final KnowledgeDocumentRepository repository;
    private final AgentRegistry agentRegistry;
    private final SaiProperties.Knowledge config;

    public KnowledgeService(VectorStore vectorStore, KnowledgeDocumentRepository repository,
            AgentRegistry agentRegistry, SaiProperties properties) {
        this.vectorStore = vectorStore;
        this.repository = repository;
        this.agentRegistry = agentRegistry;
        this.config = properties.knowledge();
    }

    public List<KnowledgeDocument> list(String agentId) {
        requireKnowledgeAgent(agentId);
        return repository.findByAgent(agentId);
    }

    @Transactional
    public KnowledgeDocument ingest(String agentId, MultipartFile file, String uploadedBy) throws IOException {
        requireKnowledgeAgent(agentId);
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Die Datei ist leer");
        }
        String filename = file.getOriginalFilename() == null ? "dokument" : file.getOriginalFilename();
        UUID documentId = UUID.randomUUID();

        ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        List<Document> extracted = new TikaDocumentReader(resource).get();
        TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(config.chunkSize()).build();
        List<Document> chunks = splitter.apply(extracted).stream()
                .filter(c -> c.getText() != null && !c.getText().isBlank())
                .map(c -> new Document(c.getText(), Map.of(
                        META_AGENT_ID, agentId,
                        META_DOCUMENT_ID, documentId.toString(),
                        META_FILENAME, filename)))
                .toList();
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Aus der Datei konnte kein Text extrahiert werden");
        }
        vectorStore.add(chunks);

        KnowledgeDocument document = new KnowledgeDocument(documentId, agentId, filename, file.getContentType(),
                file.getSize(), chunks.size(), uploadedBy, Instant.now());
        repository.insert(document);
        return document;
    }

    @Transactional
    public void delete(String agentId, UUID documentId) {
        requireKnowledgeAgent(agentId);
        repository.find(agentId, documentId)
                .orElseThrow(() -> new NotFoundException("Dokument nicht gefunden"));
        vectorStore.delete(new FilterExpressionBuilder().eq(META_DOCUMENT_ID, documentId.toString()).build());
        repository.delete(documentId);
    }

    public SearchRequest searchRequest(String agentId, String query) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .topK(config.topK())
                .similarityThreshold(config.similarityThreshold())
                .filterExpression(agentFilter(agentId));
        if (query != null) {
            builder.query(query);
        }
        return builder.build();
    }

    public String searchAsText(String agentId, String query) {
        List<Document> results = vectorStore.similaritySearch(searchRequest(agentId, query));
        if (results == null || results.isEmpty()) {
            return "Keine passenden Inhalte in der Wissensbasis gefunden.";
        }
        StringBuilder text = new StringBuilder();
        for (Document doc : results) {
            text.append("[Quelle: ").append(doc.getMetadata().getOrDefault(META_FILENAME, "?")).append("]\n")
                    .append(doc.getText()).append("\n\n");
        }
        return text.toString().strip();
    }

    public static Filter.Expression agentFilter(String agentId) {
        return new FilterExpressionBuilder().eq(META_AGENT_ID, agentId).build();
    }

    private AgentDefinition requireKnowledgeAgent(String agentId) {
        AgentDefinition agent = agentRegistry.get(agentId);
        if (!agent.usesKnowledge()) {
            throw new IllegalArgumentException("Agent " + agentId + " hat keine Wissensbasis aktiviert");
        }
        return agent;
    }
}
