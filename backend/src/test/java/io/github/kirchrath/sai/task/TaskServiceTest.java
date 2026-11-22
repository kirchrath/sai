package io.github.kirchrath.sai.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.kirchrath.sai.web.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskServiceTest {

    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskStepRepository steps = mock(TaskStepRepository.class);
    private final TaskService service = new TaskService(tasks, steps);
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tasks.save(any())).thenAnswer(i -> i.getArgument(0));
        when(steps.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createsTaskWithStepsFromDraft() {
        TaskDto dto = service.create("alice", "assistent", conversationId, new TaskDraft("  Umzug planen ",
                " Alles fuer den Umzug ", TaskPriority.HIGH, LocalDate.of(2026, 12, 1),
                Arrays.asList(" Kartons kaufen ", null, "  ", "Helfer fragen")));

        assertThat(dto.title()).isEqualTo("Umzug planen");
        assertThat(dto.description()).isEqualTo("Alles fuer den Umzug");
        assertThat(dto.status()).isEqualTo(TaskStatus.OPEN);
        assertThat(dto.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(dto.dueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(dto.agentId()).isEqualTo("assistent");
        assertThat(dto.conversationId()).isEqualTo(conversationId);
        assertThat(dto.steps()).extracting(TaskStep::position, TaskStep::text, TaskStep::done).containsExactly(
                org.assertj.core.groups.Tuple.tuple(0, "Kartons kaufen", false),
                org.assertj.core.groups.Tuple.tuple(1, "Helfer fragen", false));
        assertThat(dto.steps()).allSatisfy(step -> assertThat(step.taskId()).isEqualTo(dto.id()));
    }

    @Test
    void appliesDefaultsAndLimits() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < TaskService.MAX_STEPS + 5; i++) {
            many.add("x".repeat(TaskService.MAX_STEP_TEXT + 10));
        }

        TaskDto dto = service.create("alice", "assistent", conversationId,
                new TaskDraft("T".repeat(300), null, null, null, many));

        assertThat(dto.title()).hasSize(TaskService.MAX_TITLE);
        assertThat(dto.description()).isEmpty();
        assertThat(dto.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(dto.dueDate()).isNull();
        assertThat(dto.steps()).hasSize(TaskService.MAX_STEPS)
                .allSatisfy(step -> assertThat(step.text()).hasSize(TaskService.MAX_STEP_TEXT));
    }

    @Test
    void createsTaskWithoutSteps() {
        assertThat(service.create("alice", "a", conversationId, new TaskDraft("T", "", TaskPriority.LOW, null, null))
                .steps()).isEmpty();
    }

    @Test
    void titleIsRequired() {
        assertThatThrownBy(() -> service.create("alice", "a", conversationId, new TaskDraft(null, "", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("alice", "a", conversationId, new TaskDraft("  ", "", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tasks, never()).save(any());
    }

    @Test
    void listsByConversationStatusOrAllWithSteps() {
        Task one = Task.create("alice", "a", conversationId, "Eins", "", TaskPriority.LOW, null);
        Task two = Task.create("alice", "a", null, "Zwei", "", TaskPriority.LOW, null);
        TaskStep step = TaskStep.create(one.id(), 0, "Schritt", false);
        when(steps.findByTaskIdInOrderByPositionAsc(anyCollection())).thenReturn(List.of(step));
        when(tasks.findByUserIdAndConversationIdOrderByCreatedAtAsc("alice", conversationId)).thenReturn(List.of(one));
        when(tasks.findByUserIdAndStatusOrderByCreatedAtDesc("alice", TaskStatus.OPEN)).thenReturn(List.of(one, two));
        when(tasks.findByUserIdOrderByCreatedAtDesc("alice")).thenReturn(List.of());

        assertThat(service.list("alice", TaskStatus.DONE, conversationId)).singleElement()
                .satisfies(t -> assertThat(t.steps()).containsExactly(step));
        assertThat(service.list("alice", TaskStatus.OPEN, null)).extracting(TaskDto::title, t -> t.steps().size())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Eins", 1),
                        org.assertj.core.groups.Tuple.tuple("Zwei", 0));
        assertThat(service.list("alice", null, null)).isEmpty();
    }

    @Test
    void getsOwnTaskWithSteps() {
        Task task = Task.create("alice", "a", null, "T", "", TaskPriority.LOW, null);
        TaskStep step = TaskStep.create(task.id(), 0, "S", true);
        when(tasks.findByIdAndUserId(task.id(), "alice")).thenReturn(Optional.of(task));
        when(steps.findByTaskIdOrderByPositionAsc(task.id())).thenReturn(List.of(step));

        assertThat(service.get(task.id(), "alice").steps()).containsExactly(step);
        assertThatThrownBy(() -> service.get(task.id(), "mallory")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSyncsStepsAndUsesClientVersion() {
        Task task = Task.create("alice", "a", conversationId, "Alt", "Alt", TaskPriority.LOW, null);
        TaskStep keep = TaskStep.create(task.id(), 0, "Behalten", false);
        TaskStep drop = TaskStep.create(task.id(), 1, "Entfernen", false);
        when(tasks.findByIdAndUserId(task.id(), "alice")).thenReturn(Optional.of(task));
        when(steps.findByTaskIdOrderByPositionAsc(task.id())).thenReturn(List.of(keep, drop));

        TaskDto dto = service.update(task.id(), "alice", new TaskUpdateDto(3L, " Neu ", " Text ", TaskStatus.DONE,
                TaskPriority.HIGH, LocalDate.of(2027, 1, 1), List.of(
                        new TaskUpdateDto.Step(null, " Neuer Schritt ", true),
                        new TaskUpdateDto.Step(keep.id(), "Behalten!", true))));

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(tasks).save(saved.capture());
        assertThat(saved.getValue().version()).isEqualTo(3L);
        assertThat(saved.getValue().createdAt()).isEqualTo(task.createdAt());
        assertThat(dto.title()).isEqualTo("Neu");
        assertThat(dto.description()).isEqualTo("Text");
        assertThat(dto.status()).isEqualTo(TaskStatus.DONE);
        assertThat(dto.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(dto.dueDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(dto.steps()).extracting(TaskStep::position, TaskStep::text, TaskStep::done).containsExactly(
                org.assertj.core.groups.Tuple.tuple(0, "Neuer Schritt", true),
                org.assertj.core.groups.Tuple.tuple(1, "Behalten!", true));
        assertThat(dto.steps().get(1).id()).isEqualTo(keep.id());
        ArgumentCaptor<Collection<TaskStep>> deleted = ArgumentCaptor.forClass(Collection.class);
        verify(steps).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(drop);
    }

    @Test
    void unchangedStepsAreNotWrittenAgain() {
        Task task = Task.create("alice", "a", null, "T", "", TaskPriority.LOW, null);
        TaskStep same = TaskStep.create(task.id(), 0, "Gleich", false);
        TaskStep renamed = TaskStep.create(task.id(), 1, "Alt", false);
        TaskStep toggled = TaskStep.create(task.id(), 2, "Abhaken", false);
        TaskStep moved = TaskStep.create(task.id(), 5, "Verschoben", false);
        when(tasks.findByIdAndUserId(task.id(), "alice")).thenReturn(Optional.of(task));
        when(steps.findByTaskIdOrderByPositionAsc(task.id())).thenReturn(List.of(same, renamed, toggled, moved));

        TaskDto dto = service.update(task.id(), "alice", new TaskUpdateDto(0L, "T", "", TaskStatus.OPEN,
                TaskPriority.LOW, null, List.of(
                        new TaskUpdateDto.Step(same.id(), "Gleich", false),
                        new TaskUpdateDto.Step(renamed.id(), "Neu", false),
                        new TaskUpdateDto.Step(toggled.id(), "Abhaken", true),
                        new TaskUpdateDto.Step(null, "Zusatz", false),
                        new TaskUpdateDto.Step(moved.id(), "Verschoben", false))));

        // Nur geaenderte Schritte werden geschrieben (Position, Text oder Status)
        assertThat(dto.steps().getFirst()).isSameAs(same);
        verify(steps, never()).save(same);
        verify(steps).save(renamed.withChanges(1, "Neu", false));
        verify(steps).save(toggled.withChanges(2, "Abhaken", true));
        verify(steps).save(moved.withChanges(4, "Verschoben", false));
        assertThat(dto.steps()).extracting(TaskStep::text)
                .containsExactly("Gleich", "Neu", "Abhaken", "Zusatz", "Verschoben");
    }

    @Test
    void updateRejectsForeignSteps() {
        Task task = Task.create("alice", "a", null, "T", "", TaskPriority.LOW, null);
        when(tasks.findByIdAndUserId(task.id(), "alice")).thenReturn(Optional.of(task));
        when(steps.findByTaskIdOrderByPositionAsc(task.id())).thenReturn(List.of());

        assertThatThrownBy(() -> service.update(task.id(), "alice", new TaskUpdateDto(0L, "T", "", TaskStatus.OPEN,
                TaskPriority.LOW, null, List.of(new TaskUpdateDto.Step(UUID.randomUUID(), "x", false)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Unbekannter Schritt");
    }

    @Test
    void deletesOwnTask() {
        Task task = Task.create("alice", "a", null, "T", "", TaskPriority.LOW, null);
        when(tasks.findByIdAndUserId(task.id(), "alice")).thenReturn(Optional.of(task));

        service.delete(task.id(), "alice");

        verify(tasks).delete(task);
    }
}
