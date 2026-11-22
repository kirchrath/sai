package io.github.kirchrath.sai.conversation;

import io.github.kirchrath.sai.task.TaskDto;

/**
 * Ereignisse eines Chat-Streams; werden vom Controller als Server-Sent Events ausgeliefert.
 */
public sealed interface ChatEvent {

    /** Textstueck der Antwort. */
    record Token(String text) implements ChatEvent {
    }

    /** Antwort vollstaendig und gespeichert. */
    record Done(ChatMessage message) implements ChatEvent {
    }

    /** Fehler; der Stream endet danach. */
    record Error(String message) implements ChatEvent {
    }

    /** Der Agent hat eine Aufgabe angelegt (strukturiertes Ergebnis). */
    record TaskCreated(TaskDto task) implements ChatEvent {
    }

    /** Keepalive, damit Proxies die Verbindung nicht schliessen. */
    record Heartbeat() implements ChatEvent {
    }
}
