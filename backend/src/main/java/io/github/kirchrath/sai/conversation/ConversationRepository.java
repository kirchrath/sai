package io.github.kirchrath.sai.conversation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private final JdbcClient jdbc;

    public ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Conversation c) {
        jdbc.sql("""
                INSERT INTO conversation (id, user_id, agent_id, title, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .params(c.id(), c.userId(), c.agentId(), c.title(), Timestamp.from(c.createdAt()),
                        Timestamp.from(c.updatedAt()))
                .update();
    }

    public List<Conversation> findByUser(String userId) {
        return jdbc.sql("SELECT * FROM conversation WHERE user_id = ? ORDER BY updated_at DESC")
                .param(userId)
                .query(Conversation.class)
                .list();
    }

    public Optional<Conversation> find(UUID id, String userId) {
        return jdbc.sql("SELECT * FROM conversation WHERE id = ? AND user_id = ?")
                .params(id, userId)
                .query(Conversation.class)
                .optional();
    }

    public void updateTitle(UUID id, String title) {
        jdbc.sql("UPDATE conversation SET title = ? WHERE id = ?").params(title, id).update();
    }

    public void touch(UUID id, Instant when) {
        jdbc.sql("UPDATE conversation SET updated_at = ? WHERE id = ?").params(Timestamp.from(when), id).update();
    }

    public void delete(UUID id) {
        jdbc.sql("DELETE FROM conversation WHERE id = ?").param(id).update();
    }

    public void insertMessage(ChatMessage m) {
        jdbc.sql("""
                INSERT INTO chat_message (id, conversation_id, role, content, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)
                .params(m.id(), m.conversationId(), m.role().name(), m.content(), Timestamp.from(m.createdAt()))
                .update();
    }

    public List<ChatMessage> findMessages(UUID conversationId) {
        return jdbc.sql("SELECT * FROM chat_message WHERE conversation_id = ? ORDER BY created_at, id")
                .param(conversationId)
                .query(ChatMessage.class)
                .list();
    }
}
