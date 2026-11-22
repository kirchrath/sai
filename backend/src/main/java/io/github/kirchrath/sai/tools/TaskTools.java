package io.github.kirchrath.sai.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.kirchrath.sai.task.TaskDraft;
import io.github.kirchrath.sai.task.TaskDto;
import io.github.kirchrath.sai.task.TaskListener;
import io.github.kirchrath.sai.task.TaskPriority;
import io.github.kirchrath.sai.task.TaskService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Strukturiertes Ergebnis "Aufgabe": Das Modell liefert die Aufgabe als JSON (Tool-Argumente nach Schema),
 * sie wird gespeichert und als JSON an das Modell zurueckgegeben. Reine Informationen beantwortet das Modell
 * stattdessen als Markdown.
 */
@Component
public class TaskTools {

    private final TaskService taskService;

    public TaskTools(TaskService taskService) {
        this.taskService = taskService;
    }

    @Tool(name = "create_task", description = """
            Legt eine Aufgabe fuer den Benutzer an und speichert sie. Verwende dieses Tool immer dann, wenn das \
            Ergebnis eine umzusetzende Aufgabe, ein To-do oder ein Arbeitsauftrag ist (z.B. "erstelle eine \
            Aufgabe", "plane", "erinnere mich", "was muss ich tun um ..."). Zerlege die Aufgabe in konkrete \
            Schritte. Fuer reine Informationen dieses Tool NICHT verwenden, sondern in Markdown antworten. \
            Liefert die gespeicherte Aufgabe als JSON.""")
    public TaskDto createTask(
            @ToolParam(description = "Kurzer, praegnanter Titel (max. 200 Zeichen)") String title,
            @ToolParam(description = "Beschreibung: Ziel, Kontext und Ergebnis der Aufgabe") String description,
            @ToolParam(description = "Prioritaet: LOW, MEDIUM oder HIGH") TaskPriority priority,
            @ToolParam(required = false, description = "Faelligkeitsdatum im Format YYYY-MM-DD, falls bekannt")
            String dueDate,
            @ToolParam(required = false, description = "Checkliste: konkrete Arbeitsschritte in sinnvoller Reihenfolge")
            List<String> steps,
            ToolContext toolContext) {
        Map<String, Object> context = toolContext.getContext();
        TaskDto task = taskService.create(
                (String) context.get(ToolContextKeys.USER_ID),
                (String) context.get(ToolContextKeys.AGENT_ID),
                UUID.fromString((String) context.get(ToolContextKeys.CONVERSATION_ID)),
                new TaskDraft(title, description, priority, parseDate(dueDate), steps));
        if (context.get(ToolContextKeys.TASK_LISTENER) instanceof TaskListener listener) {
            listener.taskCreated(task);
        }
        return task;
    }

    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
