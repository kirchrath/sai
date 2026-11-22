package io.github.kirchrath.sai.conversation;

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

    /** Keepalive, damit Proxies die Verbindung nicht schliessen. */
    record Heartbeat() implements ChatEvent {
    }
}
