package io.github.kirchrath.sai.task;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Aenderung einer Aufgabe aus dem UI. Schritte ohne ID werden angelegt, fehlende geloescht.
 */
public record TaskUpdateDto(
        @NotNull Long version,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 20_000) String description,
        @NotNull TaskStatus status,
        @NotNull TaskPriority priority,
        LocalDate dueDate,
        @NotNull @Size(max = 100) List<@Valid Step> steps) {

    public record Step(UUID id, @NotBlank @Size(max = 500) String text, boolean done) {
    }
}
