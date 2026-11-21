package io.github.kirchrath.sai.knowledge;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Wissensdokumente pro Agent. Lesen: alle angemeldeten Benutzer; Hochladen/Loeschen: Admin-Rolle
 * (abgesichert in der SecurityConfig).
 */
@RestController
@RequestMapping("/api/agents/{agentId}/documents")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    List<KnowledgeDocument> list(@PathVariable String agentId) {
        return knowledgeService.list(agentId);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    KnowledgeDocument upload(@PathVariable String agentId, @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String user = jwt.getClaimAsString("preferred_username");
        return knowledgeService.ingest(agentId, file, user != null ? user : jwt.getSubject());
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String agentId, @PathVariable UUID documentId) {
        knowledgeService.delete(agentId, documentId);
    }
}
