package io.github.kirchrath.sai.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

public interface TaskStepRepository extends ListCrudRepository<TaskStep, UUID> {

    List<TaskStep> findByTaskIdOrderByPositionAsc(UUID taskId);

    List<TaskStep> findByTaskIdInOrderByPositionAsc(Collection<UUID> taskIds);
}
