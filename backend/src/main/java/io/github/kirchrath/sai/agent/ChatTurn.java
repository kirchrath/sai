package io.github.kirchrath.sai.agent;

import io.github.kirchrath.sai.task.TaskListener;

/**
 * Ein Chat-Turn: Benutzernachricht an einen Agenten innerhalb einer Unterhaltung.
 */
public record ChatTurn(AgentDefinition agent, String conversationId, String userId, String message,
        TaskListener taskListener) {
}
