package io.github.kirchrath.sai.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.kirchrath.sai.config.PersistenceConfig;
import io.github.kirchrath.sai.conversation.ChatMessage;
import io.github.kirchrath.sai.conversation.ChatMessageRepository;
import io.github.kirchrath.sai.conversation.Conversation;
import io.github.kirchrath.sai.conversation.ConversationRepository;
import io.github.kirchrath.sai.knowledge.KnowledgeDocument;
import io.github.kirchrath.sai.knowledge.KnowledgeDocumentRepository;
import io.github.kirchrath.sai.task.Task;
import io.github.kirchrath.sai.task.TaskPriority;
import io.github.kirchrath.sai.task.TaskRepository;
import io.github.kirchrath.sai.task.TaskStatus;
import io.github.kirchrath.sai.task.TaskStep;
import io.github.kirchrath.sai.task.TaskStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Prueft Flyway-Schema, Record-Mapping, Auditing (created/modified), Versionierung und abgeleitete Queries
 * gegen ein echtes PostgreSQL mit pgvector.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainer.class, PersistenceConfig.class})
class RepositoryIntegrationTest {

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ChatMessageRepository messages;

    @Autowired
    KnowledgeDocumentRepository documents;

    @Autowired
    TaskRepository tasks;

    @Autowired
    TaskStepRepository steps;

    @Autowired
    JdbcClient jdbc;

    @Test
    void insertsWithAuditingAndVersion() {
        Conversation saved = conversations.save(Conversation.create("alice", "assistent", "Titel"));

        assertThat(saved.version()).isZero();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.modifiedAt()).isNotNull();
        assertThat(conversations.findById(saved.id())).contains(saved);
    }

    @Test
    void updateIncrementsVersionAndModifiedDateButKeepsCreatedDate() throws InterruptedException {
        Conversation saved = conversations.save(Conversation.create("alice", "assistent", "Alt"));
        Thread.sleep(5);

        Conversation updated = conversations.save(saved.withTitle("Neu"));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.createdAt()).isEqualTo(saved.createdAt());
        assertThat(updated.modifiedAt()).isAfter(saved.modifiedAt());
        assertThat(conversations.findById(saved.id()).orElseThrow().title()).isEqualTo("Neu");
    }

    @Test
    void staleVersionIsRejected() {
        Conversation saved = conversations.save(Conversation.create("alice", "assistent", "Titel"));
        conversations.save(saved.withTitle("Erste Aenderung"));

        assertThatThrownBy(() -> conversations.save(saved.withTitle("Veraltet")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void conversationQueriesAreScopedToUserAndSortedByModification() throws InterruptedException {
        Conversation older = conversations.save(Conversation.create("bob", "assistent", "Aelter"));
        Thread.sleep(5);
        Conversation newer = conversations.save(Conversation.create("bob", "assistent", "Neuer"));
        conversations.save(Conversation.create("carol", "assistent", "Fremd"));

        assertThat(conversations.findByUserIdOrderByModifiedAtDesc("bob")).extracting(Conversation::id)
                .containsExactly(newer.id(), older.id());
        assertThat(conversations.findByIdAndUserId(older.id(), "bob")).isPresent();
        assertThat(conversations.findByIdAndUserId(older.id(), "carol")).isEmpty();
    }

    @Test
    void messagesAreOrderedAndDeletedWithConversation() throws InterruptedException {
        Conversation conversation = conversations.save(Conversation.create("dave", "assistent", "Chat"));
        ChatMessage first = messages.save(ChatMessage.create(conversation.id(), ChatMessage.Role.USER, "Frage"));
        Thread.sleep(5);
        ChatMessage second = messages.save(ChatMessage.create(conversation.id(), ChatMessage.Role.ASSISTANT, "Antwort"));

        assertThat(first.version()).isZero();
        assertThat(first.createdAt()).isNotNull();
        assertThat(first.modifiedAt()).isNotNull();
        assertThat(messages.findByConversationIdOrderByCreatedAtAsc(conversation.id()))
                .extracting(ChatMessage::id, ChatMessage::role)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first.id(), ChatMessage.Role.USER),
                        org.assertj.core.groups.Tuple.tuple(second.id(), ChatMessage.Role.ASSISTANT));

        conversations.delete(conversation);

        assertThat(messages.findByConversationIdOrderByCreatedAtAsc(conversation.id())).isEmpty();
    }

    @Test
    void knowledgeDocumentsAreScopedToAgent() {
        KnowledgeDocument doc = documents.save(KnowledgeDocument.create(UUID.randomUUID(), "wissen", "a.pdf",
                "application/pdf", 1234, 3, "admin"));
        documents.save(KnowledgeDocument.create(UUID.randomUUID(), "anderer", "b.txt", null, 1, 1, "admin"));

        assertThat(doc.version()).isZero();
        assertThat(doc.createdAt()).isNotNull();
        assertThat(doc.modifiedAt()).isNotNull();
        assertThat(documents.findByAgentIdOrderByCreatedAtDesc("wissen")).containsExactly(doc);
        assertThat(documents.findByIdAndAgentId(doc.id(), "wissen")).contains(doc);
        assertThat(documents.findByIdAndAgentId(doc.id(), "anderer")).isEmpty();
    }

    @Test
    void tasksWithStepsAreAuditedAndScoped() {
        Conversation conversation = conversations.save(Conversation.create("erin", "assistent", "Chat"));
        Task task = tasks.save(Task.create("erin", "assistent", conversation.id(), "Umzug planen", "Beschreibung",
                TaskPriority.HIGH, LocalDate.of(2026, 12, 24)));
        TaskStep second = steps.save(TaskStep.create(task.id(), 1, "Kartons packen", false));
        TaskStep first = steps.save(TaskStep.create(task.id(), 0, "Termin festlegen", true));

        assertThat(task.version()).isZero();
        assertThat(task.status()).isEqualTo(TaskStatus.OPEN);
        assertThat(task.createdAt()).isNotNull();
        assertThat(first.version()).isZero();
        assertThat(first.modifiedAt()).isNotNull();
        assertThat(tasks.findByIdAndUserId(task.id(), "erin")).contains(task);
        assertThat(tasks.findByIdAndUserId(task.id(), "mallory")).isEmpty();
        assertThat(tasks.findByUserIdOrderByCreatedAtDesc("erin")).containsExactly(task);
        assertThat(tasks.findByUserIdAndStatusOrderByCreatedAtDesc("erin", TaskStatus.OPEN)).containsExactly(task);
        assertThat(tasks.findByUserIdAndStatusOrderByCreatedAtDesc("erin", TaskStatus.DONE)).isEmpty();
        assertThat(tasks.findByUserIdAndConversationIdOrderByCreatedAtAsc("erin", conversation.id()))
                .containsExactly(task);
        assertThat(steps.findByTaskIdOrderByPositionAsc(task.id())).containsExactly(first, second);
        assertThat(steps.findByTaskIdInOrderByPositionAsc(List.of(task.id()))).containsExactly(first, second);

        TaskStep toggled = steps.save(second.withChanges(1, "Kartons packen", true));
        assertThat(toggled.version()).isEqualTo(1);

        // Unterhaltung loeschen behaelt die Aufgabe, Aufgabe loeschen entfernt die Schritte
        conversations.delete(conversation);
        assertThat(tasks.findById(task.id()).orElseThrow().conversationId()).isNull();
        tasks.deleteById(task.id());
        assertThat(steps.findByTaskIdOrderByPositionAsc(task.id())).isEmpty();
    }

    @Test
    void springAiTablesExist() {
        assertThat(jdbc.sql("SELECT count(*) FROM vector_store").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM ai_session").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM ai_session_event").query(Long.class).single()).isZero();
    }
}
