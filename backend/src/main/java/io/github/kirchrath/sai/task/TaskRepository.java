package io.github.kirchrath.sai.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

public interface TaskRepository extends ListCrudRepository<Task, UUID> {

    List<Task> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Task> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, TaskStatus status);

    List<Task> findByUserIdAndConversationIdOrderByCreatedAtAsc(String userId, UUID conversationId);

    Optional<Task> findByIdAndUserId(UUID id, String userId);
}
