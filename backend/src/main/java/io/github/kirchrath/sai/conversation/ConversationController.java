package io.github.kirchrath.sai.conversation;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.kirchrath.sai.agent.AgentChatService;
import io.github.kirchrath.sai.agent.AgentDefinition;
import io.github.kirchrath.sai.agent.AgentRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversations;
    private final AgentRegistry agentRegistry;
    private final AgentChatService chatService;

    public ConversationController(ConversationService conversations, AgentRegistry agentRegistry,
            AgentChatService chatService) {
        this.conversations = conversations;
        this.agentRegistry = agentRegistry;
        this.chatService = chatService;
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
        Conversation conversation = conversations.get(id, jwt.getSubject());
        AgentDefinition agent = agentRegistry.get(conversation.agentId());
        conversations.append(conversation, ChatMessage.Role.USER, body.content());

        StringBuilder answer = new StringBuilder();
        AtomicBoolean saved = new AtomicBoolean();

        Flux<ServerSentEvent<Object>> tokens = chatService.stream(agent, id.toString(), body.content())
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(answer::append)
                .map(chunk -> ServerSentEvent.<Object>builder(Map.of("t", chunk)).event("token").build());

        Flux<ServerSentEvent<Object>> done = Flux.defer(() -> {
            saved.set(true);
            ChatMessage message = conversations.append(conversation, ChatMessage.Role.ASSISTANT, answer.toString());
            return Flux.just(ServerSentEvent.<Object>builder(message).event("done").build());
        }).subscribeOn(Schedulers.boundedElastic());

        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<Object>builder().comment("keepalive").build());

        // Der LLM-Stream wird genau einmal abonniert; der Heartbeat endet mit dem "done"-Event.
        return Flux.merge(tokens.concatWith(done), heartbeat)
                .takeUntil(event -> "done".equals(event.event()))
                .onErrorResume(e -> {
                    log.warn("Chat-Stream fuer Unterhaltung {} fehlgeschlagen", id, e);
                    savePartial(conversation, answer, saved, "\n\n[Fehler: Antwort unvollstaendig]");
                    return Flux.just(ServerSentEvent.<Object>builder(Map.of("message", describe(e)))
                            .event("error").build());
                })
                .doOnCancel(() -> savePartial(conversation, answer, saved, "\n\n[abgebrochen]"));
    }

    private void savePartial(Conversation conversation, StringBuilder answer, AtomicBoolean saved, String suffix) {
        if (!answer.isEmpty() && saved.compareAndSet(false, true)) {
            conversations.append(conversation, ChatMessage.Role.ASSISTANT, answer + suffix);
        }
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
