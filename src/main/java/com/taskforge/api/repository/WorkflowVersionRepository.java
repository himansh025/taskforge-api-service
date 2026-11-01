package com.taskforge.api.repository;

import com.taskforge.api.entity.WorkflowVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, UUID> {
    List<WorkflowVersionEntity> findByWorkflowIdOrderByVersionNumberDesc(UUID workflowId);
    Optional<WorkflowVersionEntity> findTopByWorkflowIdOrderByVersionNumberDesc(UUID workflowId);
    Optional<WorkflowVersionEntity> findByWorkflowIdAndVersionNumber(UUID workflowId, Integer versionNumber);
}
