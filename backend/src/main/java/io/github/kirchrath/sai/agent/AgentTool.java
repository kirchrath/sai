package io.github.kirchrath.sai.agent;

/**
 * Eingebaute Tools, die in der Agent-Konfiguration aktiviert werden koennen
 * (YAML-Schreibweise z.B. {@code datetime}, {@code web-fetch}, {@code tasks}).
 */
public enum AgentTool {
    DATETIME,
    CALCULATOR,
    WEB_FETCH,
    KNOWLEDGE,
    /** Strukturiertes Ergebnis: Aufgaben als JSON anlegen und speichern. */
    TASKS
}
