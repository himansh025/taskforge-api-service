package com.taskforge.api.controller;

import com.taskforge.api.entity.WorkflowEntity;
import com.taskforge.api.entity.WorkflowVersionEntity;
import com.taskforge.api.service.WorkflowService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping("/workspaces/{workspaceId}/workflows")
    public ResponseEntity<List<WorkflowEntity>> listWorkflows(@PathVariable("workspaceId") UUID workspaceId) {
        return ResponseEntity.ok(workflowService.listWorkflows(workspaceId));
    }

    @PostMapping("/workspaces/{workspaceId}/workflows")
    public ResponseEntity<WorkflowEntity> createWorkflow(
            @PathVariable("workspaceId") UUID workspaceId,
            @RequestBody CreateWorkflowRequest req) {
        return ResponseEntity.ok(workflowService.createWorkflow(workspaceId, req.getName(), req.getDescription()));
    }

    @GetMapping("/workflows/{workflowId}")
    public ResponseEntity<WorkflowEntity> getWorkflow(@PathVariable("workflowId") UUID workflowId) {
        return ResponseEntity.ok(workflowService.getWorkflow(workflowId));
    }

    @PutMapping("/workflows/{workflowId}/draft")
    public ResponseEntity<WorkflowEntity> updateDraft(
            @PathVariable("workflowId") UUID workflowId,
            @RequestBody UpdateDraftRequest req) {
        return ResponseEntity.ok(workflowService.updateDraft(workflowId, req.getName(), req.getDescription(), req.getDraftDefinition()));
    }

    @PostMapping("/workflows/{workflowId}/validate")
    public ResponseEntity<WorkflowService.ValidationResult> validateWorkflow(@PathVariable("workflowId") UUID workflowId) {
        return ResponseEntity.ok(workflowService.validateWorkflow(workflowId));
    }

    @PostMapping("/workflows/{workflowId}/publish")
    public ResponseEntity<WorkflowVersionEntity> publishWorkflow(@PathVariable("workflowId") UUID workflowId) {
        return ResponseEntity.ok(workflowService.publishWorkflow(workflowId));
    }

    @GetMapping("/workflows/{workflowId}/versions")
    public ResponseEntity<List<WorkflowVersionEntity>> listVersions(@PathVariable("workflowId") UUID workflowId) {
        return ResponseEntity.ok(workflowService.listVersions(workflowId));
    }

    @PostMapping("/workflows/{workflowId}/executions")
    public ResponseEntity<Map<String, Object>> triggerExecution(
            @PathVariable("workflowId") UUID workflowId,
            @RequestBody(required = false) Map<String, Object> input) {
        UUID executionId = workflowService.triggerExecution(workflowId, input);
        return ResponseEntity.accepted().body(Map.of(
                "executionId", executionId,
                "status", "SUBMITTED",
                "message", "Workflow execution requested successfully"
        ));
    }

    @Data
    public static class CreateWorkflowRequest {
        private String name;
        private String description;
    }

    @Data
    public static class UpdateDraftRequest {
        private String name;
        private String description;
        private String draftDefinition;
    }
}
