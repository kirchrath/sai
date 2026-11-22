package io.github.kirchrath.sai.task;

/** Wird benachrichtigt, wenn waehrend eines Chat-Turns eine Aufgabe angelegt wurde. */
@FunctionalInterface
public interface TaskListener {

    void taskCreated(TaskDto task);
}
