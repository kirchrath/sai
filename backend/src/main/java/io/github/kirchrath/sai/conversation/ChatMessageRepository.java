package io.github.kirchrath.sai.conversation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

public interface ChatMessageRepository extends ListCrudRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
