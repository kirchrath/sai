package io.github.kirchrath.sai.conversation;

import java.util.List;
import java.util.UUID;

import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.web.NotFoundException;
import org.springframework.ai.session.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    static final String DEFAULT_TITLE = "Neue Unterhaltung";
    static final int MAX_TITLE_LENGTH = 60;

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final AgentRegistry agentRegistry;
    private final SessionService sessionService;

    public ConversationService(ConversationRepository conversations, ChatMessageRepository messages,
            AgentRegistry agentRegistry, SessionService sessionService) {
        this.conversations = conversations;
        this.messages = messages;
        this.agentRegistry = agentRegistry;
        this.sessionService = sessionService;
    }

    public List<Conversation> list(String userId) {
        return conversations.findByUserIdOrderByModifiedAtDesc(userId);
    }

    public Conversation create(String userId, String agentId) {
        agentRegistry.get(agentId);
        return conversations.save(Conversation.create(userId, agentId, DEFAULT_TITLE));
    }

    public Conversation get(UUID id, String userId) {
        return conversations.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Unterhaltung nicht gefunden"));
    }

    public List<ChatMessage> messages(UUID id, String userId) {
        get(id, userId);
        return messages.findByConversationIdOrderByCreatedAtAsc(id);
    }

    public Conversation rename(UUID id, String userId, String title) {
        return conversations.save(get(id, userId).withTitle(sanitizeTitle(title)));
    }

    @Transactional
    public void delete(UUID id, String userId) {
        conversations.delete(get(id, userId));
        sessionService.delete(id.toString());
    }

    /**
     * Speichert eine Nachricht im anzeigbaren Verlauf, aktualisiert den Aenderungszeitpunkt der Unterhaltung
     * und setzt beim ersten Benutzerbeitrag den Titel.
     */
    @Transactional
    public ChatMessage append(UUID conversationId, ChatMessage.Role role, String content) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Unterhaltung nicht gefunden"));
        ChatMessage message = messages.save(ChatMessage.create(conversationId, role, content));
        if (role == ChatMessage.Role.USER && DEFAULT_TITLE.equals(conversation.title())) {
            conversation = conversation.withTitle(sanitizeTitle(content));
        }
        conversations.save(conversation);
        return message;
    }

    static String sanitizeTitle(String raw) {
        String title = raw == null ? "" : raw.replaceAll("\\s+", " ").strip();
        if (title.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) + "…" : title;
    }
}
