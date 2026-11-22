package io.github.kirchrath.sai.task;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Schritt der Checkliste einer Aufgabe.
 */
@Table("task_step")
public record TaskStep(
        @Id UUID id,
        @Version Long version,
        UUID taskId,
        int position,
        String text,
        boolean done,
        @CreatedDate Instant createdAt,
        @LastModifiedDate Instant modifiedAt) {

    public static TaskStep create(UUID taskId, int position, String text, boolean done) {
        return new TaskStep(UUID.randomUUID(), null, taskId, position, text, done, null, null);
    }

    public TaskStep withChanges(int newPosition, String newText, boolean newDone) {
        return new TaskStep(id, version, taskId, newPosition, newText, newDone, createdAt, modifiedAt);
    }
}
