package com.taskforge.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ExecutionController {

    private final RedisMessageListenerContainer redisContainer;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${taskforge.orchestrator.url:http://orchestrator-service:8081}")
    private String orchestratorUrl;

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<Object> getExecution(@PathVariable UUID executionId) {
        RestClient client = restClientBuilder.baseUrl(orchestratorUrl).build();
        try {
            Object result = client.get()
                    .uri("/api/v1/engine/executions/{id}", executionId)
                    .retrieve()
                    .body(Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to query execution {}: {}", executionId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/executions/{executionId}/tasks")
    public ResponseEntity<Object> getTasks(@PathVariable UUID executionId) {
        RestClient client = restClientBuilder.baseUrl(orchestratorUrl).build();
        try {
            Object result = client.get()
                    .uri("/api/v1/engine/executions/{id}/tasks", executionId)
                    .retrieve()
                    .body(Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to query tasks for execution {}: {}", executionId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/workspaces/{workspaceId}/executions")
    public ResponseEntity<Object> listExecutions(@PathVariable UUID workspaceId) {
        RestClient client = restClientBuilder.baseUrl(orchestratorUrl).build();
        try {
            Object result = client.get()
                    .uri("/api/v1/engine/workspaces/{id}/executions", workspaceId)
                    .retrieve()
                    .body(Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to list executions for workspace {}: {}", workspaceId, e.getMessage());
            return ResponseEntity.ok(java.util.List.of());
        }
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ResponseEntity<Object> cancelExecution(@PathVariable UUID executionId) {
        RestClient client = restClientBuilder.baseUrl(orchestratorUrl).build();
        try {
            Object result = client.post()
                    .uri("/api/v1/engine/executions/{id}/cancel", executionId)
                    .retrieve()
                    .body(Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to cancel execution {}: {}", executionId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/executions/{executionId}/retry")
    public ResponseEntity<Object> retryExecution(@PathVariable UUID executionId) {
        RestClient client = restClientBuilder.baseUrl(orchestratorUrl).build();
        try {
            Object result = client.post()
                    .uri("/api/v1/engine/executions/{id}/retry", executionId)
                    .retrieve()
                    .body(Object.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to retry execution {}: {}", executionId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value = "/executions/{executionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecutionEvents(@PathVariable UUID executionId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        String channel = "execution:" + executionId + ":progress";

        // Initial snapshot from Orchestrator
        try {
            RestClient client = restClientBuilder.baseUrl(orchestratorUrl).build();
            Object initial = client.get()
                    .uri("/api/v1/engine/executions/{id}", executionId)
                    .retrieve()
                    .body(Object.class);
            if (initial != null) {
                emitter.send(SseEmitter.event().name("init").data(initial));
            }
        } catch (Exception e) {
            log.debug("Initial state fetch for SSE: {}", e.getMessage());
        }

        MessageListener listener = (Message message, byte[] pattern) -> {
            try {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                emitter.send(SseEmitter.event().name("progress").data(body));
            } catch (Exception e) {
                log.warn("Error sending SSE for execution {}: {}", executionId, e.getMessage());
            }
        };

        ChannelTopic topic = new ChannelTopic(channel);
        redisContainer.addMessageListener(listener, topic);

        Runnable cleanup = () -> redisContainer.removeMessageListener(listener, topic);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }
}
