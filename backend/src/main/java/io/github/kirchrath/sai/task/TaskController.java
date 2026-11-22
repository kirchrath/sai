package io.github.kirchrath.sai.task;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    List<TaskDto> list(@RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID conversationId, @AuthenticationPrincipal Jwt jwt) {
        return tasks.list(jwt.getSubject(), status, conversationId);
    }

    @GetMapping("/{id}")
    TaskDto get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return tasks.get(id, jwt.getSubject());
    }

    @PutMapping("/{id}")
    TaskDto update(@PathVariable UUID id, @Valid @RequestBody TaskUpdateDto body, @AuthenticationPrincipal Jwt jwt) {
        return tasks.update(id, jwt.getSubject(), body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        tasks.delete(id, jwt.getSubject());
    }
}
