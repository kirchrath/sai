package io.github.kirchrath.sai.conversation;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.kirchrath.sai.agent.AgentChatService;
import io.github.kirchrath.sai.agent.AgentDefinition;
import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.config.SaiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Fuehrt einen Chat-Turn aus: speichert die Benutzernachricht, streamt die Antwort des Agenten und speichert sie
 * am Ende (bei Fehler oder Abbruch als Teilantwort).
 */
@Service
public class ChatStreamService {

    static final String ERROR_SUFFIX = "\n\n[Fehler: Antwort unvollstaendig]";
    static final String CANCEL_SUFFIX = "\n\n[abgebrochen]";

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);

    private final ConversationService conversations;
    private final AgentRegistry agentRegistry;
    private final AgentChatService chatService;
    private final Duration heartbeatInterval;

    public ChatStreamService(ConversationService conversations, AgentRegistry agentRegistry,
            AgentChatService chatService, SaiProperties properties) {
        this.conversations = conversations;
        this.agentRegistry = agentRegistry;
        this.chatService = chatService;
        this.heartbeatInterval = properties.chat().heartbeatInterval();
    }

    public Flux<ChatEvent> send(UUID conversationId, String userId, String content) {
        Conversation conversation = conversations.get(conversationId, userId);
        AgentDefinition agent = agentRegistry.get(conversation.agentId());
        conversations.append(conversationId, ChatMessage.Role.USER, content);

        StringBuilder answer = new StringBuilder();
        AtomicBoolean saved = new AtomicBoolean();

        Flux<ChatEvent> tokens = chatService.stream(agent, conversationId.toString(), content)
                .filter(chunk -> !chunk.isEmpty())
                .doOnNext(answer::append)
                .map(ChatEvent.Token::new);

        Flux<ChatEvent> done = Flux.<ChatEvent>defer(() -> {
            saved.set(true);
            return Flux.just(new ChatEvent.Done(
                    conversations.append(conversationId, ChatMessage.Role.ASSISTANT, answer.toString())));
        }).subscribeOn(Schedulers.boundedElastic());

        Flux<ChatEvent> heartbeat = Flux.interval(heartbeatInterval).map(i -> new ChatEvent.Heartbeat());

        // Der LLM-Stream wird genau einmal abonniert; der Heartbeat endet mit dem Done-Event.
        return Flux.merge(tokens.concatWith(done), heartbeat)
                .takeUntil(ChatEvent.Done.class::isInstance)
                .onErrorResume(e -> {
                    log.warn("Chat-Stream fuer Unterhaltung {} fehlgeschlagen", conversationId, e);
                    savePartial(conversationId, answer, saved, ERROR_SUFFIX);
                    return Flux.just(new ChatEvent.Error(describe(e)));
                })
                .doOnCancel(() -> savePartial(conversationId, answer, saved, CANCEL_SUFFIX));
    }

    private void savePartial(UUID conversationId, StringBuilder answer, AtomicBoolean saved, String suffix) {
        if (!answer.isEmpty() && saved.compareAndSet(false, true)) {
            conversations.append(conversationId, ChatMessage.Role.ASSISTANT, answer + suffix);
        }
    }

    /** Fehlermeldung inkl. Grundursache, z.B. "Request failed (401 Unauthorized)". */
    static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String top = message(e);
        String cause = message(root);
        return top.contains(cause) ? top : top + " (" + cause + ")";
    }

    private static String message(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
