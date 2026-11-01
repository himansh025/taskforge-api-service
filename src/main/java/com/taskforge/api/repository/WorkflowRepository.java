package com.taskforge.api.repository;

import com.taskforge.api.entity.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {
    List<WorkflowEntity> findByWorkspaceId(UUID workspaceId);
    Optional<WorkflowEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
