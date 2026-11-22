package io.github.kirchrath.sai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import io.github.kirchrath.sai.TestAgents;
import io.github.kirchrath.sai.agent.AgentChatService;
import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.agent.ChatTurn;
import io.github.kirchrath.sai.task.Task;
import io.github.kirchrath.sai.task.TaskDto;
import io.github.kirchrath.sai.task.TaskPriority;
import io.github.kirchrath.sai.config.SaiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ChatStreamServiceTest {

    private final ConversationService conversations = mock(ConversationService.class);
    private final AgentChatService chat = mock(AgentChatService.class);
    private final AgentRegistry registry = new AgentRegistry(
            new SaiProperties(null, null, null, null, List.of(TestAgents.plain("assistent"))));
    private final Conversation conversation = Conversation.create("alice", "assistent", "Titel");
    private final ChatMessage stored = ChatMessage.create(conversation.id(), ChatMessage.Role.ASSISTANT, "Hallo");

    private ChatStreamService service(Duration heartbeat) {
        return new ChatStreamService(conversations, registry, chat,
                new SaiProperties(null, new SaiProperties.Chat(30, heartbeat), null, null, null));
    }

    @BeforeEach
    void setUp() {
        when(conversations.get(conversation.id(), "alice")).thenReturn(conversation);
        when(conversations.append(eq(conversation.id()), eq(ChatMessage.Role.ASSISTANT), anyString()))
                .thenReturn(stored);
    }

    @Test
    void streamsTokensAndStoresAnswer() {
        when(chat.stream(any(ChatTurn.class))).thenReturn(Flux.just("Hal", "", "lo"));

        StepVerifier.create(service(Duration.ofHours(1)).send(conversation.id(), "alice", "Hi"))
                .expectNext(new ChatEvent.Token("Hal"), new ChatEvent.Token("lo"), new ChatEvent.Done(stored))
                .verifyComplete();

        verify(conversations).append(conversation.id(), ChatMessage.Role.USER, "Hi");
        verify(conversations).append(conversation.id(), ChatMessage.Role.ASSISTANT, "Hallo");
        ArgumentCaptor<ChatTurn> turn = ArgumentCaptor.forClass(ChatTurn.class);
        verify(chat).stream(turn.capture());
        assertThat(turn.getValue().agent()).isEqualTo(registry.get("assistent"));
        assertThat(turn.getValue().conversationId()).isEqualTo(conversation.id().toString());
        assertThat(turn.getValue().userId()).isEqualTo("alice");
        assertThat(turn.getValue().message()).isEqualTo("Hi");
    }

    @Test
    void emitsCreatedTasksAsEvents() {
        TaskDto task = TaskDto.of(Task.create("alice", "assistent", conversation.id(), "Aufgabe", "", TaskPriority.HIGH,
                null), List.of());
        when(chat.stream(any(ChatTurn.class))).thenAnswer(invocation -> {
            ChatTurn turn = invocation.getArgument(0);
            return Flux.defer(() -> {
                turn.taskListener().taskCreated(task);
                return Flux.just("Angelegt.");
            });
        });

        List<ChatEvent> events = service(Duration.ofHours(1)).send(conversation.id(), "alice", "Plane etwas")
                .collectList().block();

        // Die Reihenfolge von Tool-Ereignis und Text haengt vom Modell ab; Done kommt immer zuletzt
        assertThat(events).containsExactlyInAnyOrder(new ChatEvent.TaskCreated(task),
                new ChatEvent.Token("Angelegt."), new ChatEvent.Done(stored));
        assertThat(events.getLast()).isEqualTo(new ChatEvent.Done(stored));
    }

    @Test
    void errorAfterTokensStoresPartialAnswer() {
        when(chat.stream(any(ChatTurn.class)))
                .thenReturn(Flux.concat(Flux.just("Teil"), Flux.error(new RuntimeException("kaputt"))));

        StepVerifier.create(service(Duration.ofHours(1)).send(conversation.id(), "alice", "Hi"))
                .expectNext(new ChatEvent.Token("Teil"), new ChatEvent.Error("kaputt"))
                .verifyComplete();

        verify(conversations).append(conversation.id(), ChatMessage.Role.ASSISTANT,
                "Teil" + ChatStreamService.ERROR_SUFFIX);
    }

    @Test
    void errorWithoutTokensStoresNothing() {
        when(chat.stream(any(ChatTurn.class))).thenReturn(Flux.error(new IllegalStateException()));

        StepVerifier.create(service(Duration.ofHours(1)).send(conversation.id(), "alice", "Hi"))
                .expectNext(new ChatEvent.Error("IllegalStateException"))
                .verifyComplete();

        verify(conversations, never()).append(eq(conversation.id()), eq(ChatMessage.Role.ASSISTANT), anyString());
    }

    @Test
    void failingStoreIsNotRetriedAsPartialAnswer() {
        when(chat.stream(any(ChatTurn.class))).thenReturn(Flux.just("Antwort"));
        when(conversations.append(eq(conversation.id()), eq(ChatMessage.Role.ASSISTANT), anyString()))
                .thenThrow(new RuntimeException("DB weg"));

        StepVerifier.create(service(Duration.ofHours(1)).send(conversation.id(), "alice", "Hi"))
                .expectNext(new ChatEvent.Token("Antwort"), new ChatEvent.Error("DB weg"))
                .verifyComplete();

        verify(conversations, times(1)).append(eq(conversation.id()), eq(ChatMessage.Role.ASSISTANT), anyString());
    }

    @Test
    void cancelStoresPartialAnswer() {
        when(chat.stream(any(ChatTurn.class))).thenReturn(Flux.concat(Flux.just("Halb"), Flux.never()));

        StepVerifier.create(service(Duration.ofHours(1)).send(conversation.id(), "alice", "Hi"))
                .expectNext(new ChatEvent.Token("Halb"))
                .thenCancel()
                .verify();

        verify(conversations).append(conversation.id(), ChatMessage.Role.ASSISTANT,
                "Halb" + ChatStreamService.CANCEL_SUFFIX);
    }

    @Test
    void cancelWithoutTokensStoresNothing() {
        when(chat.stream(any(ChatTurn.class))).thenReturn(Flux.never());

        StepVerifier.create(service(Duration.ofHours(1)).send(conversation.id(), "alice", "Hi"))
                .thenCancel()
                .verify();

        verify(conversations, never()).append(eq(conversation.id()), eq(ChatMessage.Role.ASSISTANT), anyString());
    }

    @Test
    void emitsHeartbeatsWhileWaiting() {
        when(chat.stream(any(ChatTurn.class))).thenReturn(Flux.never());

        StepVerifier.withVirtualTime(() -> service(Duration.ofSeconds(15)).send(conversation.id(), "alice", "Hi"))
                .thenAwait(Duration.ofSeconds(30))
                .expectNext(new ChatEvent.Heartbeat(), new ChatEvent.Heartbeat())
                .thenCancel()
                .verify();
    }

    @Test
    void describesErrorsWithRootCause() {
        assertThat(ChatStreamService.describe(new RuntimeException("Request failed",
                new IllegalStateException("x", new IOException("401 Unauthorized")))))
                .isEqualTo("Request failed (401 Unauthorized)");
        assertThat(ChatStreamService.describe(new RuntimeException(new IOException("timeout"))))
                .isEqualTo("java.io.IOException: timeout");
        assertThat(ChatStreamService.describe(new RuntimeException(" "))).isEqualTo("RuntimeException");
    }
}
