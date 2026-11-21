package io.github.kirchrath.sai.config;

import io.github.kirchrath.sai.tools.CalculatorTools;
import io.github.kirchrath.sai.tools.DateTimeTools;
import io.github.kirchrath.sai.tools.McpServerTools;
import io.github.kirchrath.sai.tools.WebFetchTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registriert die Tools, die der MCP-Server (Streamable HTTP unter /mcp) anbietet.
 */
@Configuration(proxyBeanMethods = false)
public class McpServerConfig {

    @Bean
    ToolCallbackProvider mcpTools(DateTimeTools dateTime, CalculatorTools calculator, WebFetchTools webFetch,
            McpServerTools serverTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateTime, calculator, webFetch, serverTools)
                .build();
    }
}
