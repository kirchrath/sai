package io.github.kirchrath.sai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ChatMemoryConfig {

    /** Kontextfenster fuer das Modell; der vollstaendige Verlauf liegt zusaetzlich in chat_message. */
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, SaiProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.chat().memoryMaxMessages())
                .build();
    }
}
