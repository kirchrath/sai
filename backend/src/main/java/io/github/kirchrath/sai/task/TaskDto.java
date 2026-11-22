package io.github.kirchrath.sai.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aufgabe inkl. Checkliste, wie sie das Frontend und das Modell (Tool-Ergebnis als JSON) erhalten.
 */
public record TaskDto(
        UUID id,
        Long version,
        String agentId,
        UUID conversationId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        List<TaskStep> steps,
        Instant createdAt,
        Instant modifiedAt) {

    public static TaskDto of(Task task, List<TaskStep> steps) {
        return new TaskDto(task.id(), task.version(), task.agentId(), task.conversationId(), task.title(),
                task.description(), task.status(), task.priority(), task.dueDate(), List.copyOf(steps),
                task.createdAt(), task.modifiedAt());
    }
}
