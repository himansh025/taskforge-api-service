package com.taskforge.api.service;

import com.taskforge.api.entity.UserEntity;
import com.taskforge.api.entity.WorkspaceEntity;
import com.taskforge.api.entity.WorkspaceMemberEntity;
import com.taskforge.api.repository.UserRepository;
import com.taskforge.api.repository.WorkspaceMemberRepository;
import com.taskforge.api.repository.WorkspaceRepository;
import com.taskforge.common.model.WorkspaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WorkspaceEntity> listWorkspacesForUser(UUID userId) {
        if (userId == null) {
            return workspaceRepository.findAll();
        }
        List<UUID> workspaceIds = memberRepository.findByUserId(userId).stream()
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .toList();
        return workspaceRepository.findAllById(workspaceIds);
    }

    @Transactional(readOnly = true)
    public WorkspaceEntity getWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
    }

    @Transactional
    public WorkspaceEntity createWorkspace(UUID userId, String name, String slug) {
        if (workspaceRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Workspace slug already exists");
        }

        WorkspaceEntity workspace = WorkspaceEntity.builder()
                .name(name)
                .slug(slug)
                .createdBy(userId)
                .build();
        workspace = workspaceRepository.save(workspace);

        if (userId != null) {
            WorkspaceMemberEntity member = WorkspaceMemberEntity.builder()
                    .workspaceId(workspace.getId())
                    .userId(userId)
                    .role(WorkspaceRole.OWNER)
                    .build();
            memberRepository.save(member);
        }

        return workspace;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberEntity> listMembers(UUID workspaceId) {
        return memberRepository.findByWorkspaceId(workspaceId);
    }

    @Transactional
    public WorkspaceMemberEntity addMember(UUID workspaceId, String email, WorkspaceRole role) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        if (memberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId()).isPresent()) {
            throw new IllegalArgumentException("User is already a member of this workspace");
        }

        WorkspaceMemberEntity member = WorkspaceMemberEntity.builder()
                .workspaceId(workspaceId)
                .userId(user.getId())
                .role(role != null ? role : WorkspaceRole.VIEWER)
                .build();
        return memberRepository.save(member);
    }
}
