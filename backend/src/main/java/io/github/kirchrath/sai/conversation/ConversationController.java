package io.github.kirchrath.sai.conversation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversations;
    private final ChatStreamService chatStream;

    public ConversationController(ConversationService conversations, ChatStreamService chatStream) {
        this.conversations = conversations;
        this.chatStream = chatStream;
    }

    public record CreateRequest(@NotBlank String agentId) {
    }

    public record RenameRequest(@NotBlank @Size(max = 200) String title) {
    }

    public record SendRequest(@NotBlank @Size(max = 100_000) String content) {
    }

    @GetMapping
    List<Conversation> list(@AuthenticationPrincipal Jwt jwt) {
        return conversations.list(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Conversation create(@Valid @RequestBody CreateRequest body, @AuthenticationPrincipal Jwt jwt) {
        return conversations.create(jwt.getSubject(), body.agentId());
    }

    @GetMapping("/{id}")
    Conversation get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return conversations.get(id, jwt.getSubject());
    }

    @PatchMapping("/{id}")
    Conversation rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest body, @AuthenticationPrincipal Jwt jwt) {
        return conversations.rename(id, jwt.getSubject(), body.title());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        conversations.delete(id, jwt.getSubject());
    }

    @GetMapping("/{id}/messages")
    List<ChatMessage> messages(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return conversations.messages(id, jwt.getSubject());
    }

    /**
     * Sendet eine Benutzernachricht und streamt die Antwort als Server-Sent Events:
     * {@code token} (Textstueck), {@code done} (gespeicherte Nachricht) bzw. {@code error}.
     */
    @PostMapping(path = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<Object>> send(@PathVariable UUID id, @Valid @RequestBody SendRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        return chatStream.send(id, jwt.getSubject(), body.content()).map(ConversationController::toSse);
    }

    private static ServerSentEvent<Object> toSse(ChatEvent event) {
        return switch (event) {
            case ChatEvent.Token token -> ServerSentEvent.<Object>builder(Map.of("t", token.text())).event("token").build();
            case ChatEvent.Done done -> ServerSentEvent.<Object>builder(done.message()).event("done").build();
            case ChatEvent.Error error -> ServerSentEvent.<Object>builder(Map.of("message", error.message()))
                    .event("error").build();
            case ChatEvent.Heartbeat heartbeat -> ServerSentEvent.<Object>builder().comment("keepalive").build();
        };
    }
}
