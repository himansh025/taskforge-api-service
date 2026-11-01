package com.taskforge.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.api.entity.ApiOutboxEntity;
import com.taskforge.api.entity.WorkflowEntity;
import com.taskforge.api.entity.WorkflowVersionEntity;
import com.taskforge.api.repository.ApiOutboxRepository;
import com.taskforge.api.repository.WorkflowRepository;
import com.taskforge.api.repository.WorkflowVersionRepository;
import com.taskforge.common.event.EventEnvelope;
import com.taskforge.common.event.WorkflowExecutionRequestedData;
import com.taskforge.common.model.WorkflowStatus;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final ApiOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<WorkflowEntity> listWorkflows(UUID workspaceId) {
        return workflowRepository.findByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public WorkflowEntity getWorkflow(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
    }

    @Transactional
    public WorkflowEntity createWorkflow(UUID workspaceId, String name, String description) {
        String defaultDraft = """
        {
          "name": "%s",
          "nodes": [
            {"id": "node-1", "type": "CSV_VALIDATE", "position": {"x": 250, "y": 50}, "data": {"label": "Validate Invoice CSV"}, "config": {"requiredColumns": ["invoiceId", "customer", "amount"]}},
            {"id": "node-2", "type": "DATA_TRANSFORM", "position": {"x": 250, "y": 180}, "data": {"label": "Cleanse & Normalize"}, "config": {}},
            {"id": "node-3", "type": "CONDITION", "position": {"x": 250, "y": 300}, "data": {"label": "Check Threshold (> $10k)"}, "config": {"threshold": 10000}},
            {"id": "node-4", "type": "GENERATE_REPORT", "position": {"x": 120, "y": 420}, "data": {"label": "Generate Summary PDF"}, "config": {}},
            {"id": "node-5", "type": "SEND_EMAIL", "position": {"x": 380, "y": 420}, "data": {"label": "Send Email Notification"}, "config": {"recipient": "finance@taskforge.local"}}
          ],
          "edges": [
            {"id": "e1-2", "source": "node-1", "target": "node-2"},
            {"id": "e2-3", "source": "node-2", "target": "node-3"},
            {"id": "e3-4", "source": "node-3", "target": "node-4", "label": "Passed"},
            {"id": "e3-5", "source": "node-3", "target": "node-5", "label": "Review Needed"}
          ]
        }
        """.formatted(name);

        WorkflowEntity entity = WorkflowEntity.builder()
                .workspaceId(workspaceId)
                .name(name)
                .description(description)
                .status(WorkflowStatus.DRAFT)
                .draftDefinition(defaultDraft)
                .build();
        return workflowRepository.save(entity);
    }

    @Transactional
    public WorkflowEntity updateDraft(UUID workflowId, String name, String description, String draftDefinition) {
        WorkflowEntity workflow = getWorkflow(workflowId);
        if (name != null && !name.isBlank()) workflow.setName(name);
        if (description != null) workflow.setDescription(description);
        if (draftDefinition != null && !draftDefinition.isBlank()) workflow.setDraftDefinition(draftDefinition);
        return workflowRepository.save(workflow);
    }

    @Transactional(readOnly = true)
    public ValidationResult validateWorkflow(UUID workflowId) {
        WorkflowEntity workflow = getWorkflow(workflowId);
        return validateDefinitionJson(workflow.getDraftDefinition());
    }

    public ValidationResult validateDefinitionJson(String json) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (json == null || json.isBlank()) {
            errors.add("Draft definition cannot be empty");
            return ValidationResult.builder().valid(false).errors(errors).warnings(warnings).build();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode nodesNode = root.get("nodes");
            JsonNode edgesNode = root.get("edges");

            if (nodesNode == null || !nodesNode.isArray() || nodesNode.isEmpty()) {
                errors.add("Workflow must contain at least one node");
                return ValidationResult.builder().valid(false).errors(errors).warnings(warnings).build();
            }

            Set<String> nodeIds = new HashSet<>();
            for (JsonNode n : nodesNode) {
                String id = n.has("id") ? n.get("id").asText() : null;
                if (id == null || id.isBlank()) {
                    errors.add("Node missing required 'id'");
                } else if (!nodeIds.add(id)) {
                    errors.add("Duplicate node id detected: " + id);
                }
            }

            // Build adjacency list for cycle detection
            Map<String, List<String>> adj = new HashMap<>();
            Map<String, Integer> inDegree = new HashMap<>();
            for (String id : nodeIds) {
                adj.put(id, new ArrayList<>());
                inDegree.put(id, 0);
            }

            if (edgesNode != null && edgesNode.isArray()) {
                for (JsonNode e : edgesNode) {
                    String source = e.has("source") ? e.get("source").asText() : (e.has("from") ? e.get("from").asText() : null);
                    String target = e.has("target") ? e.get("target").asText() : (e.has("to") ? e.get("to").asText() : null);

                    if (source == null || !nodeIds.contains(source)) {
                        errors.add("Edge has invalid source node: " + source);
                    } else if (target == null || !nodeIds.contains(target)) {
                        errors.add("Edge has invalid target node: " + target);
                    } else {
                        adj.get(source).add(target);
                        inDegree.put(target, inDegree.get(target) + 1);
                    }
                }
            }

            // Kahn's algorithm for cycle detection
            Queue<String> queue = new LinkedList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.add(entry.getKey());
                }
            }

            int visitedCount = 0;
            while (!queue.isEmpty()) {
                String u = queue.poll();
                visitedCount++;
                for (String v : adj.get(u)) {
                    inDegree.put(v, inDegree.get(v) - 1);
                    if (inDegree.get(v) == 0) {
                        queue.add(v);
                    }
                }
            }

            if (visitedCount < nodeIds.size()) {
                errors.add("Workflow graph contains a cycle/loop. TaskForge workflows must be a Directed Acyclic Graph (DAG).");
            }

        } catch (Exception e) {
            errors.add("Invalid JSON structure: " + e.getMessage());
        }

        return ValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    @Transactional
    @SneakyThrows
    public WorkflowVersionEntity publishWorkflow(UUID workflowId) {
        WorkflowEntity workflow = getWorkflow(workflowId);
        ValidationResult validation = validateDefinitionJson(workflow.getDraftDefinition());
        if (!validation.isValid()) {
            throw new IllegalStateException("Cannot publish invalid workflow: " + String.join(", ", validation.getErrors()));
        }

        String definition = workflow.getDraftDefinition();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(definition.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String hash = hexString.toString();

        int nextVersion = workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        WorkflowVersionEntity version = WorkflowVersionEntity.builder()
                .workflowId(workflowId)
                .versionNumber(nextVersion)
                .definitionJson(definition)
                .definitionHash(hash)
                .build();

        workflow.setStatus(WorkflowStatus.PUBLISHED);
        workflowRepository.save(workflow);
        return workflowVersionRepository.save(version);
    }

    @Transactional(readOnly = true)
    public List<WorkflowVersionEntity> listVersions(UUID workflowId) {
        return workflowVersionRepository.findByWorkflowIdOrderByVersionNumberDesc(workflowId);
    }

    @Transactional
    @SneakyThrows
    public UUID triggerExecution(UUID workflowId, Map<String, Object> input) {
        WorkflowEntity workflow = getWorkflow(workflowId);
        WorkflowVersionEntity latest = workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId)
                .orElseThrow(() -> new IllegalStateException("Workflow has no published version to execute"));

        UUID executionId = UUID.randomUUID();

        WorkflowExecutionRequestedData data = WorkflowExecutionRequestedData.builder()
                .executionId(executionId.toString())
                .workflowId(workflowId.toString())
                .workflowVersionId(latest.getId().toString())
                .triggerType("MANUAL")
                .input(input != null ? input : Collections.emptyMap())
                .definitionHash(latest.getDefinitionHash())
                .build();

        EventEnvelope<WorkflowExecutionRequestedData> envelope = EventEnvelope.<WorkflowExecutionRequestedData>builder()
                .eventType("workflow.execution.requested")
                .workspaceId(workflow.getWorkspaceId().toString())
                .correlationId(executionId.toString())
                .producer("api-service")
                .data(data)
                .build();

        ApiOutboxEntity outbox = ApiOutboxEntity.builder()
                .aggregateType("WorkflowExecution")
                .aggregateId(executionId.toString())
                .eventType("workflow.execution.requested")
                .topic("workflow.execution.requested.v1")
                .payload(objectMapper.writeValueAsString(envelope))
                .status("PENDING")
                .build();

        outboxRepository.save(outbox);
        return executionId;
    }

    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
    }
}
