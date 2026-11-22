package io.github.kirchrath.sai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.kirchrath.sai.TestAgents;
import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.config.SaiProperties;
import io.github.kirchrath.sai.web.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;

class ConversationServiceTest {

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatMemory memory = mock(ChatMemory.class);
    private final AgentRegistry registry = new AgentRegistry(
            new SaiProperties(null, null, null, null, List.of(TestAgents.plain("assistent"))));
    private final ConversationService service = new ConversationService(conversations, messages, registry, memory);
    private final Conversation existing = Conversation.create("alice", "assistent", ConversationService.DEFAULT_TITLE);

    @BeforeEach
    void setUp() {
        when(conversations.save(any())).thenAnswer(i -> i.getArgument(0));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        when(conversations.findByIdAndUserId(existing.id(), "alice")).thenReturn(Optional.of(existing));
        when(conversations.findById(existing.id())).thenReturn(Optional.of(existing));
    }

    @Test
    void listsConversationsOfUser() {
        when(conversations.findByUserIdOrderByModifiedAtDesc("alice")).thenReturn(List.of(existing));

        assertThat(service.list("alice")).containsExactly(existing);
    }

    @Test
    void createsConversationForKnownAgent() {
        Conversation created = service.create("alice", "assistent");

        assertThat(created.userId()).isEqualTo("alice");
        assertThat(created.agentId()).isEqualTo("assistent");
        assertThat(created.title()).isEqualTo(ConversationService.DEFAULT_TITLE);
        assertThat(created.id()).isNotNull();
        assertThat(created.version()).isNull();
    }

    @Test
    void createForUnknownAgentFails() {
        assertThatThrownBy(() -> service.create("alice", "gibtsnicht")).isInstanceOf(NotFoundException.class);
        verify(conversations, never()).save(any());
    }

    @Test
    void foreignConversationIsNotFound() {
        assertThatThrownBy(() -> service.get(existing.id(), "mallory")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsMessagesOfOwnConversation() {
        ChatMessage message = ChatMessage.create(existing.id(), ChatMessage.Role.USER, "Hi");
        when(messages.findByConversationIdOrderByCreatedAtAsc(existing.id())).thenReturn(List.of(message));

        assertThat(service.messages(existing.id(), "alice")).containsExactly(message);
    }

    @Test
    void renamesWithSanitizedTitle() {
        assertThat(service.rename(existing.id(), "alice", "  Neuer \n Titel ").title()).isEqualTo("Neuer Titel");
    }

    @Test
    void deleteRemovesConversationAndMemory() {
        service.delete(existing.id(), "alice");

        verify(conversations).delete(existing);
        verify(memory).clear(existing.id().toString());
    }

    @Test
    void firstUserMessageSetsTitle() {
        ChatMessage message = service.append(existing.id(), ChatMessage.Role.USER, "Wie spaet ist es?");

        assertThat(message.conversationId()).isEqualTo(existing.id());
        assertThat(message.role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(message.content()).isEqualTo("Wie spaet ist es?");
        assertThat(savedConversation().title()).isEqualTo("Wie spaet ist es?");
    }

    @Test
    void laterMessagesOnlyTouchConversation() {
        Conversation titled = existing.withTitle("Schon benannt");
        when(conversations.findById(existing.id())).thenReturn(Optional.of(titled));

        service.append(existing.id(), ChatMessage.Role.USER, "Noch eine Frage");

        assertThat(savedConversation()).isEqualTo(titled);
    }

    @Test
    void assistantMessageDoesNotSetTitle() {
        service.append(existing.id(), ChatMessage.Role.ASSISTANT, "Antwort");

        assertThat(savedConversation().title()).isEqualTo(ConversationService.DEFAULT_TITLE);
    }

    @Test
    void appendToUnknownConversationFails() {
        UUID unknown = UUID.randomUUID();
        when(conversations.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.append(unknown, ChatMessage.Role.USER, "x"))
                .isInstanceOf(NotFoundException.class);
    }

    private Conversation savedConversation() {
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversations).save(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "NULL|Neue Unterhaltung",
            "'   '|Neue Unterhaltung",
            "'  a  b '|a b",
            "123456789012345678901234567890123456789012345678901234567890|123456789012345678901234567890123456789012345678901234567890",
            "1234567890123456789012345678901234567890123456789012345678901|123456789012345678901234567890123456789012345678901234567890…"
    })
    void sanitizesTitles(String raw, String expected) {
        assertThat(ConversationService.sanitizeTitle(raw)).isEqualTo(expected);
    }
}
