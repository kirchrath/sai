package io.github.kirchrath.sai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.kirchrath.sai.TestAgents;
import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.agent.AgentTool;
import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.web.NotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class KnowledgeServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
    private final AgentRegistry registry = new AgentRegistry(new SaiProperties(null, null, null, null, List.of(
            TestAgents.agent("wissen", Set.of(), true),
            TestAgents.agent("tool", Set.of(AgentTool.KNOWLEDGE), false),
            TestAgents.plain("ohne"))));
    private final KnowledgeService service = new KnowledgeService(vectorStore, repository, registry,
            new SaiProperties(null, null, new SaiProperties.Knowledge(50, 4, 0.6), null, null));

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void listsDocumentsOfKnowledgeAgent() {
        KnowledgeDocument doc = KnowledgeDocument.create(UUID.randomUUID(), "tool", "a.txt", null, 1, 1, "admin");
        when(repository.findByAgentIdOrderByCreatedAtDesc("tool")).thenReturn(List.of(doc));

        assertThat(service.list("tool")).containsExactly(doc);
    }

    @Test
    void rejectsAgentsWithoutKnowledge() {
        assertThatThrownBy(() -> service.list("ohne")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ohne");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestsChunksWithMetadata() throws IOException {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        String text = "Der Wartungsslot ist dienstags von 18 bis 20 Uhr. ".repeat(20);

        KnowledgeDocument doc = service.ingest("wissen", file("handbuch.txt", text), "admin");

        ArgumentCaptor<List<Document>> chunks = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunks.capture());
        assertThat(chunks.getValue()).hasSizeGreaterThan(1).allSatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsEntry(KnowledgeService.META_AGENT_ID, "wissen")
                .containsEntry(KnowledgeService.META_DOCUMENT_ID, doc.id().toString())
                .containsEntry(KnowledgeService.META_FILENAME, "handbuch.txt"));
        assertThat(doc.agentId()).isEqualTo("wissen");
        assertThat(doc.filename()).isEqualTo("handbuch.txt");
        assertThat(doc.contentType()).isEqualTo("text/plain");
        assertThat(doc.sizeBytes()).isEqualTo(text.length());
        assertThat(doc.chunkCount()).isEqualTo(chunks.getValue().size());
        assertThat(doc.uploadedBy()).isEqualTo("admin");
        assertThat(doc.version()).isNull();
    }

    @Test
    void usesDefaultFilenameWhenMissing() throws IOException {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        MultipartFile upload = mock(MultipartFile.class);
        when(upload.isEmpty()).thenReturn(false);
        when(upload.getOriginalFilename()).thenReturn(null);
        when(upload.getBytes()).thenReturn("Inhalt".getBytes(StandardCharsets.UTF_8));

        assertThat(service.ingest("wissen", upload, "admin").filename()).isEqualTo("dokument");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> service.ingest("wissen", file("leer.txt", ""), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Die Datei ist leer");
    }

    @Test
    void rejectsFileWithoutText() {
        assertThatThrownBy(() -> service.ingest("wissen", file("leer.txt", "   \n  "), "admin"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kein Text");
        verify(vectorStore, never()).add(any());
    }

    @Test
    void wrapsEmbeddingFailures() {
        doThrow(new RuntimeException("offline")).when(vectorStore).add(any());

        assertThatThrownBy(() -> service.ingest("wissen", file("a.txt", "Ein kurzer Text"), "admin"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Embedding fehlgeschlagen: offline");
        verify(repository, never()).save(any());
    }

    @Test
    void deletesDocumentAndChunks() {
        UUID id = UUID.randomUUID();
        KnowledgeDocument doc = KnowledgeDocument.create(id, "wissen", "a.txt", null, 1, 1, "admin");
        when(repository.findByIdAndAgentId(id, "wissen")).thenReturn(Optional.of(doc));

        service.delete("wissen", id);

        ArgumentCaptor<Filter.Expression> filter = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(filter.capture());
        assertThat(filter.getValue().toString()).contains(KnowledgeService.META_DOCUMENT_ID).contains(id.toString());
        verify(repository).delete(doc);
    }

    @Test
    void deleteOfUnknownDocumentFails() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndAgentId(id, "wissen")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("wissen", id)).isInstanceOf(NotFoundException.class);
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    void buildsSearchRequestScopedToAgent() {
        SearchRequest withQuery = service.searchRequest("wissen", "Wartung");
        SearchRequest withoutQuery = service.searchRequest("wissen", null);

        assertThat(withQuery.getQuery()).isEqualTo("Wartung");
        assertThat(withQuery.getTopK()).isEqualTo(4);
        assertThat(withQuery.getSimilarityThreshold()).isEqualTo(0.6);
        assertThat(withQuery.getFilterExpression()).isEqualTo(KnowledgeService.agentFilter("wissen"));
        assertThat(withoutQuery.getQuery()).isEmpty();
    }

    @Test
    void formatsSearchResultsWithSources() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("Dienstags", Map.of(KnowledgeService.META_FILENAME, "handbuch.pdf")),
                new Document("Ohne Quelle")));

        assertThat(service.searchAsText("wissen", "Wartung"))
                .isEqualTo("[Quelle: handbuch.pdf]\nDienstags\n\n[Quelle: ?]\nOhne Quelle");
    }

    @Test
    void reportsMissingResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null, List.of());

        assertThat(service.searchAsText("wissen", "x")).startsWith("Keine passenden Inhalte");
        assertThat(service.searchAsText("wissen", "x")).startsWith("Keine passenden Inhalte");
    }
}
