package com.taskforge.api.controller;

import com.taskforge.api.entity.UserEntity;
import com.taskforge.api.entity.WorkspaceEntity;
import com.taskforge.api.entity.WorkspaceMemberEntity;
import com.taskforge.api.service.WorkspaceService;
import com.taskforge.common.model.WorkspaceRole;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    public ResponseEntity<List<WorkspaceEntity>> listWorkspaces(@AuthenticationPrincipal UserEntity user) {
        UUID userId = user != null ? user.getId() : null;
        return ResponseEntity.ok(workspaceService.listWorkspacesForUser(userId));
    }

    @PostMapping
    public ResponseEntity<WorkspaceEntity> createWorkspace(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody CreateWorkspaceRequest req) {
        UUID userId = user != null ? user.getId() : null;
        String slug = req.getSlug() != null && !req.getSlug().isBlank()
                ? req.getSlug()
                : "ws-" + UUID.randomUUID().toString().substring(0, 8);
        return ResponseEntity.ok(workspaceService.createWorkspace(userId, req.getName(), slug));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceEntity> getWorkspace(@PathVariable("workspaceId") UUID workspaceId) {
        return ResponseEntity.ok(workspaceService.getWorkspace(workspaceId));
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberEntity>> listMembers(@PathVariable("workspaceId") UUID workspaceId) {
        return ResponseEntity.ok(workspaceService.listMembers(workspaceId));
    }

    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<WorkspaceMemberEntity> addMember(
            @PathVariable("workspaceId") UUID workspaceId,
            @RequestBody AddMemberRequest req) {
        return ResponseEntity.ok(workspaceService.addMember(workspaceId, req.getEmail(), req.getRole()));
    }

    @Data
    public static class CreateWorkspaceRequest {
        private String name;
        private String slug;
    }

    @Data
    public static class AddMemberRequest {
        private String email;
        private WorkspaceRole role;
    }
}
