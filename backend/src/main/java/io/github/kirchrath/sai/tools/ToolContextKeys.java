package io.github.kirchrath.sai.tools;

/** Schluessel im ToolContext eines Chat-Turns. Werte setzt das Backend, nie das Modell. */
public final class ToolContextKeys {

    public static final String AGENT_ID = "agentId";
    public static final String USER_ID = "userId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String TASK_LISTENER = "taskListener";

    private ToolContextKeys() {
    }
}
