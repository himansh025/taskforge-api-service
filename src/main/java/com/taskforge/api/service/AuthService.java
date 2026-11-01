package com.taskforge.api.service;

import com.taskforge.api.entity.UserEntity;
import com.taskforge.api.entity.WorkspaceEntity;
import com.taskforge.api.entity.WorkspaceMemberEntity;
import com.taskforge.api.repository.UserRepository;
import com.taskforge.api.repository.WorkspaceMemberRepository;
import com.taskforge.api.repository.WorkspaceRepository;
import com.taskforge.api.security.JwtService;
import com.taskforge.common.model.WorkspaceRole;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(String email, String password, String displayName) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        UserEntity user = UserEntity.builder()
                .email(email.trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName.trim())
                .status("ACTIVE")
                .build();
        user = userRepository.save(user);

        // Create default workspace for new user
        String slug = "ws-" + user.getId().toString().substring(0, 8);
        WorkspaceEntity workspace = WorkspaceEntity.builder()
                .name(displayName + "'s Workspace")
                .slug(slug)
                .createdBy(user.getId())
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMemberEntity membership = WorkspaceMemberEntity.builder()
                .workspaceId(workspace.getId())
                .userId(user.getId())
                .role(WorkspaceRole.OWNER)
                .build();
        memberRepository.save(membership);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .defaultWorkspaceId(workspace.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        List<WorkspaceMemberEntity> memberships = memberRepository.findByUserId(user.getId());
        UUID defaultWorkspaceId = memberships.isEmpty() ? null : memberships.get(0).getWorkspaceId();

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .defaultWorkspaceId(defaultWorkspaceId)
                .build();
    }

    @Data
    @Builder
    public static class AuthResponse {
        private String token;
        private UUID userId;
        private String email;
        private String displayName;
        private UUID defaultWorkspaceId;
    }
}
