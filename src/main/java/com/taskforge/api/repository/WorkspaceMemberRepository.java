package com.taskforge.api.repository;

import com.taskforge.api.entity.WorkspaceMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, UUID> {
    List<WorkspaceMemberEntity> findByUserId(UUID userId);
    List<WorkspaceMemberEntity> findByWorkspaceId(UUID workspaceId);
    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
