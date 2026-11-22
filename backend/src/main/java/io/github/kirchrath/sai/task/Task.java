package io.github.kirchrath.sai.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Aufgabe eines Benutzers, angelegt von einem Agenten.
 */
@Table("task")
public record Task(
        @Id UUID id,
        @Version Long version,
        String userId,
        String agentId,
        UUID conversationId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant modifiedAt) {

    public static Task create(String userId, String agentId, UUID conversationId, String title, String description,
            TaskPriority priority, LocalDate dueDate) {
        return new Task(UUID.randomUUID(), null, userId, agentId, conversationId, title, description, TaskStatus.OPEN,
                priority, dueDate, null, null);
    }

    /** Uebernimmt bearbeitbare Felder; die Version des Clients dient dem optimistischen Locking. */
    public Task withChanges(Long expectedVersion, String newTitle, String newDescription, TaskStatus newStatus,
            TaskPriority newPriority, LocalDate newDueDate) {
        return new Task(id, expectedVersion, userId, agentId, conversationId, newTitle, newDescription, newStatus,
                newPriority, newDueDate, createdAt, modifiedAt);
    }
}
