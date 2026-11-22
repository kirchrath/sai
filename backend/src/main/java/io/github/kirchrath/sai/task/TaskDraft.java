package io.github.kirchrath.sai.task;

import java.time.LocalDate;
import java.util.List;

/**
 * Vom Modell geliefertes Ergebnis "Aufgabe", noch nicht gespeichert.
 */
public record TaskDraft(String title, String description, TaskPriority priority, LocalDate dueDate,
        List<String> steps) {
}
