package io.github.kirchrath.sai.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.kirchrath.sai.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    static final int MAX_TITLE = 200;
    static final int MAX_STEP_TEXT = 500;
    static final int MAX_STEPS = 100;

    private final TaskRepository tasks;
    private final TaskStepRepository steps;

    public TaskService(TaskRepository tasks, TaskStepRepository steps) {
        this.tasks = tasks;
        this.steps = steps;
    }

    /** Legt eine Aufgabe aus dem strukturierten Modell-Ergebnis an. */
    @Transactional
    public TaskDto create(String userId, String agentId, UUID conversationId, TaskDraft draft) {
        String title = truncate(draft.title() == null ? "" : draft.title().strip(), MAX_TITLE);
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Eine Aufgabe braucht einen Titel");
        }
        Task task = tasks.save(Task.create(userId, agentId, conversationId, title,
                draft.description() == null ? "" : draft.description().strip(),
                draft.priority() == null ? TaskPriority.MEDIUM : draft.priority(), draft.dueDate()));

        List<TaskStep> created = new ArrayList<>();
        List<String> texts = draft.steps() == null ? List.of() : draft.steps();
        for (String text : texts) {
            if (text != null && !text.isBlank() && created.size() < MAX_STEPS) {
                created.add(steps.save(
                        TaskStep.create(task.id(), created.size(), truncate(text.strip(), MAX_STEP_TEXT), false)));
            }
        }
        return TaskDto.of(task, created);
    }

    /** Eigene Aufgaben, optional gefiltert nach Unterhaltung oder Status. */
    public List<TaskDto> list(String userId, TaskStatus status, UUID conversationId) {
        List<Task> found;
        if (conversationId != null) {
            found = tasks.findByUserIdAndConversationIdOrderByCreatedAtAsc(userId, conversationId);
        } else if (status != null) {
            found = tasks.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            found = tasks.findByUserIdOrderByCreatedAtDesc(userId);
        }
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<TaskStep>> stepsByTask = steps
                .findByTaskIdInOrderByPositionAsc(found.stream().map(Task::id).toList()).stream()
                .collect(Collectors.groupingBy(TaskStep::taskId));
        return found.stream().map(t -> TaskDto.of(t, stepsByTask.getOrDefault(t.id(), List.of()))).toList();
    }

    public TaskDto get(UUID id, String userId) {
        Task task = find(id, userId);
        return TaskDto.of(task, steps.findByTaskIdOrderByPositionAsc(id));
    }

    /** Aktualisiert Aufgabe und Checkliste; eine veraltete Version fuehrt zu einem Konflikt. */
    @Transactional
    public TaskDto update(UUID id, String userId, TaskUpdateDto update) {
        Task current = find(id, userId);
        Task saved = tasks.save(current.withChanges(update.version(), update.title().strip(),
                update.description().strip(), update.status(), update.priority(), update.dueDate()));

        Map<UUID, TaskStep> existing = new HashMap<>(steps.findByTaskIdOrderByPositionAsc(id).stream()
                .collect(Collectors.toMap(TaskStep::id, Function.identity())));
        List<TaskStep> result = new ArrayList<>();
        for (TaskUpdateDto.Step step : update.steps()) {
            int position = result.size();
            String text = step.text().strip();
            if (step.id() == null) {
                result.add(steps.save(TaskStep.create(id, position, text, step.done())));
            } else {
                TaskStep known = existing.remove(step.id());
                if (known == null) {
                    throw new IllegalArgumentException("Unbekannter Schritt: " + step.id());
                }
                boolean unchanged = known.position() == position && known.text().equals(text)
                        && known.done() == step.done();
                result.add(unchanged ? known : steps.save(known.withChanges(position, text, step.done())));
            }
        }
        steps.deleteAll(existing.values());
        return TaskDto.of(saved, result);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        tasks.delete(find(id, userId));
    }

    private Task find(UUID id, String userId) {
        return tasks.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Aufgabe nicht gefunden"));
    }

    private static String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
