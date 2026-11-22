package io.github.kirchrath.sai.conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

public interface ConversationRepository extends ListCrudRepository<Conversation, UUID> {

    List<Conversation> findByUserIdOrderByModifiedAtDesc(String userId);

    Optional<Conversation> findByIdAndUserId(UUID id, String userId);
}
