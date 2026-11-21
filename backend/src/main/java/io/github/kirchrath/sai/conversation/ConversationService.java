package io.github.kirchrath.sai.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.kirchrath.sai.agent.AgentRegistry;
import io.github.kirchrath.sai.web.NotFoundException;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    static final String DEFAULT_TITLE = "Neue Unterhaltung";
    private static final int MAX_TITLE = 200;

    private final ConversationRepository repository;
    private final AgentRegistry agentRegistry;
    private final ChatMemory chatMemory;

    public ConversationService(ConversationRepository repository, AgentRegistry agentRegistry, ChatMemory chatMemory) {
        this.repository = repository;
        this.agentRegistry = agentRegistry;
        this.chatMemory = chatMemory;
    }

    public List<Conversation> list(String userId) {
        return repository.findByUser(userId);
    }

    public Conversation create(String userId, String agentId) {
        agentRegistry.get(agentId);
        Instant now = Instant.now();
        Conversation conversation = new Conversation(UUID.randomUUID(), userId, agentId, DEFAULT_TITLE, now, now);
        repository.insert(conversation);
        return conversation;
    }

    public Conversation get(UUID id, String userId) {
        return repository.find(id, userId).orElseThrow(() -> new NotFoundException("Unterhaltung nicht gefunden"));
    }

    public List<ChatMessage> messages(UUID id, String userId) {
        get(id, userId);
        return repository.findMessages(id);
    }

    public Conversation rename(UUID id, String userId, String title) {
        get(id, userId);
        repository.updateTitle(id, sanitizeTitle(title));
        return get(id, userId);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        get(id, userId);
        repository.delete(id);
        chatMemory.clear(id.toString());
    }

    /** Speichert eine Nachricht im anzeigbaren Verlauf und setzt beim ersten Beitrag den Titel. */
    @Transactional
    public ChatMessage append(Conversation conversation, ChatMessage.Role role, String content) {
        Instant now = Instant.now();
        ChatMessage message = new ChatMessage(UUID.randomUUID(), conversation.id(), role, content, now);
        repository.insertMessage(message);
        repository.touch(conversation.id(), now);
        if (role == ChatMessage.Role.USER && DEFAULT_TITLE.equals(conversation.title())) {
            repository.updateTitle(conversation.id(), sanitizeTitle(content));
        }
        return message;
    }

    static String sanitizeTitle(String raw) {
        String title = raw == null ? "" : raw.replaceAll("\\s+", " ").strip();
        if (title.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return title.length() > 60 ? title.substring(0, 60) + "…" : title.substring(0, Math.min(title.length(), MAX_TITLE));
    }
}
