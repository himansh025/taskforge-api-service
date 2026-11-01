package com.taskforge.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "definition_json", nullable = false, columnDefinition = "JSONB")
    private String definitionJson;

    @Column(name = "definition_hash", nullable = false, length = 64)
    private String definitionHash;

    @Column(name = "published_by")
    private UUID publishedBy;

    @CreationTimestamp
    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;
}
