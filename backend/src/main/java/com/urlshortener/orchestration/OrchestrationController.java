package com.urlshortener.orchestration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orchestration")
@Tag(name = "Orchestration", description = "Governed SDLC workflow demonstration")
public class OrchestrationController {
    private final WorkflowOrchestrator orchestrator;

    public OrchestrationController(WorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/runs")
    @Operation(summary = "Create a governed SDLC workflow run")
    public ResponseEntity<WorkflowRunState> create(@Valid @RequestBody CreateRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                orchestrator.create(request.scenario(), request.requirement(), request.demoFault()));
    }

    @GetMapping("/runs")
    @Operation(summary = "List workflow runs")
    public List<WorkflowRunState> list() {
        return orchestrator.list();
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Get workflow state, dependency graph, context, and audit events")
    public WorkflowRunState get(@PathVariable String id) {
        return orchestrator.get(id);
    }

    @PostMapping("/runs/{id}/advance")
    @Operation(summary = "Execute the next dependency-ready task wave")
    public WorkflowRunState advance(@PathVariable String id) {
        return orchestrator.advance(id);
    }

    @PostMapping("/runs/{id}/tasks/{taskId}/approve")
    @Operation(summary = "Approve a high-impact task")
    public WorkflowRunState approve(@PathVariable String id, @PathVariable String taskId,
                                    @Valid @RequestBody ReviewerAction action) {
        return orchestrator.approve(id, taskId, action.actor(), action.comment(), true);
    }

    @PostMapping("/runs/{id}/tasks/{taskId}/reject")
    @Operation(summary = "Reject a high-impact task and stop the run")
    public WorkflowRunState reject(@PathVariable String id, @PathVariable String taskId,
                                   @Valid @RequestBody ReviewerAction action) {
        return orchestrator.approve(id, taskId, action.actor(), action.comment(), false);
    }

    @PostMapping("/runs/{id}/safe-stop")
    @Operation(summary = "Safely stop a run between execution waves")
    public WorkflowRunState safeStop(@PathVariable String id, @RequestBody(required = false) ReviewerAction action) {
        return orchestrator.safeStop(id, actor(action), comment(action));
    }

    @PostMapping("/runs/{id}/resume")
    @Operation(summary = "Resume a safely stopped run")
    public WorkflowRunState resume(@PathVariable String id, @RequestBody(required = false) ReviewerAction action) {
        return orchestrator.resume(id, actor(action));
    }

    @PostMapping("/runs/{id}/rollback")
    @Operation(summary = "Withdraw generated artifacts while preserving the audit trail")
    public WorkflowRunState rollback(@PathVariable String id, @RequestBody(required = false) ReviewerAction action) {
        return orchestrator.rollback(id, actor(action), comment(action));
    }

    @PostMapping("/runs/{id}/replan")
    @Operation(summary = "Rebuild the plan after the upstream requirement changes")
    public WorkflowRunState replan(@PathVariable String id, @Valid @RequestBody ReplanRequest request) {
        return orchestrator.replan(id, request.requirement(), request.actor());
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get run success, retry, rollback, replan, and latency metrics")
    public WorkflowMetrics metrics() {
        return orchestrator.metrics();
    }

    private String actor(ReviewerAction action) {
        return action == null ? "human" : action.actor();
    }

    private String comment(ReviewerAction action) {
        return action == null ? null : action.comment();
    }

    public record CreateRunRequest(@NotBlank String scenario, @NotBlank String requirement,
                                   String demoFault) {
    }

    public record ReviewerAction(@NotBlank String actor, String comment) {
    }

    public record ReplanRequest(@NotBlank String requirement, @NotBlank String actor) {
    }
}