package io.github.kirchrath.sai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.kirchrath.sai.task.Task;
import io.github.kirchrath.sai.task.TaskDraft;
import io.github.kirchrath.sai.task.TaskDto;
import io.github.kirchrath.sai.task.TaskListener;
import io.github.kirchrath.sai.task.TaskPriority;
import io.github.kirchrath.sai.task.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class TaskToolsTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskTools tools = new TaskTools(taskService);
    private final UUID conversationId = UUID.randomUUID();
    private final TaskDto created = TaskDto.of(
            Task.create("alice", "assistent", conversationId, "T", "", TaskPriority.LOW, null), List.of());

    private Map<String, Object> context() {
        Map<String, Object> context = new HashMap<>();
        context.put(ToolContextKeys.USER_ID, "alice");
        context.put(ToolContextKeys.AGENT_ID, "assistent");
        context.put(ToolContextKeys.CONVERSATION_ID, conversationId.toString());
        return context;
    }

    @Test
    void createsTaskAndNotifiesListener() {
        when(taskService.create(eq("alice"), eq("assistent"), eq(conversationId), any())).thenReturn(created);
        List<TaskDto> notified = new ArrayList<>();
        Map<String, Object> context = context();
        context.put(ToolContextKeys.TASK_LISTENER, (TaskListener) notified::add);

        TaskDto result = tools.createTask("Titel", "Beschreibung", TaskPriority.HIGH, "2026-12-24",
                List.of("A", "B"), new ToolContext(context));

        assertThat(result).isSameAs(created);
        assertThat(notified).containsExactly(created);
        verify(taskService).create("alice", "assistent", conversationId, new TaskDraft("Titel", "Beschreibung",
                TaskPriority.HIGH, LocalDate.of(2026, 12, 24), List.of("A", "B")));
    }

    @Test
    void worksWithoutListener() {
        when(taskService.create(any(), any(), any(), any())).thenReturn(created);

        assertThat(tools.createTask("T", "", TaskPriority.LOW, null, null, new ToolContext(context())))
                .isSameAs(created);
    }

    @Test
    void parsesDatesLeniently() {
        assertThat(TaskTools.parseDate(null)).isNull();
        assertThat(TaskTools.parseDate(" ")).isNull();
        assertThat(TaskTools.parseDate(" 2026-02-01 ")).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(TaskTools.parseDate("morgen")).isNull();
    }

    @Test
    void exposesJsonSchemaForStructuredTasks() throws NoSuchMethodException {
        ToolCallback callback = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks()[0];
        String schema = callback.getToolDefinition().inputSchema();

        assertThat(callback.getToolDefinition().name()).isEqualTo("create_task");
        assertThat(schema).contains("\"title\"", "\"description\"", "\"priority\"", "\"dueDate\"", "\"steps\"")
                .contains("HIGH").doesNotContain("toolContext");
        Method method = TaskTools.class.getMethod("createTask", String.class, String.class, TaskPriority.class,
                String.class, List.class, ToolContext.class);
        assertThat(method.getReturnType()).isEqualTo(TaskDto.class);
    }
}
