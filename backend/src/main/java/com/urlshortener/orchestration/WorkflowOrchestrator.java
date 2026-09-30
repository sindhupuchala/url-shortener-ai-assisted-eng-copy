package com.urlshortener.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@Service
public class WorkflowOrchestrator {
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_REQUIREMENT_LENGTH = 2000;
    private static final Set<String> DEMO_FAULTS = Set.of("NONE", "TRANSIENT_TEST_FAILURE", "DOCUMENTATION_FALLBACK");
    private static final Set<String> SUCCESS_TASK_STATES = Set.of("SUCCEEDED", "SUCCEEDED_WITH_FALLBACK");
    private static final Set<String> TERMINAL_RUN_STATES = Set.of("COMPLETED", "FAILED", "REJECTED", "ROLLED_BACK");
    private static final String SECRET_PATTERN = "(?i)\\b(api[_ -]?key|password|secret|token)\\b\\s*[:=]\\s*[\\\"']?[A-Za-z0-9._-]{10,}";

    private final OrchestrationRunRepository repository;
    private final ObjectMapper objectMapper;
    private final WorkflowAgent agent;

    public WorkflowOrchestrator(OrchestrationRunRepository repository, ObjectMapper objectMapper,
                                WorkflowAgent agent) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.agent = agent;
    }

    @Transactional
    public synchronized WorkflowRunState create(String scenarioValue, String requirement, String demoFaultValue) {
        String normalizedRequirement = requirement == null ? "" : requirement.trim();
        if (normalizedRequirement.isBlank() || normalizedRequirement.length() > MAX_REQUIREMENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Requirement must contain between 1 and " + MAX_REQUIREMENT_LENGTH + " characters");
        }
        if (normalizedRequirement.matches(".*" + SECRET_PATTERN + ".*")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Requirement appears to contain a secret; remove it before starting a run");
        }

        String scenario = normalizeScenario(scenarioValue);
        String demoFault = normalizeFault(demoFaultValue);
        Instant now = Instant.now();
        WorkflowRunState state = new WorkflowRunState();
        state.id = UUID.randomUUID().toString();
        state.scenario = scenario;
        state.requirement = normalizedRequirement;
        state.status = "READY";
        state.demoFault = demoFault;
        state.planRevision = 1;
        state.createdAt = now;
        state.updatedAt = now;
        state.tasks = buildTasks();
        addEvent(state, "human", "RUN_CREATED", null,
                "Run created with explicit dependency graph and policy checks.");
        persist(state);
        return state;
    }

    @Transactional
    public synchronized WorkflowRunState get(String id) {
        return load(id);
    }

    @Transactional
    public synchronized List<WorkflowRunState> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(record -> deserialize(record.getSnapshotJson()))
                .toList();
    }

    @Transactional
    public synchronized WorkflowRunState advance(String id) {
        WorkflowRunState state = load(id);
        if ("STOPPED".equals(state.status)) {
            throw conflict("Run is safe-stopped; resume it before advancing");
        }
        if (TERMINAL_RUN_STATES.contains(state.status)) {
            throw conflict("Run is terminal; replan to create a new execution revision");
        }

        refreshTaskStatuses(state);
        List<WorkflowTaskState> ready = state.tasks.stream()
                .filter(task -> "READY".equals(task.status))
                .toList();
        if (ready.isEmpty()) {
            settleRunState(state);
            state.updatedAt = Instant.now();
            persist(state);
            return state;
        }

        Map<String, String> upstream = state.tasks.stream()
                .filter(task -> task.output != null && SUCCESS_TASK_STATES.contains(task.status))
                .collect(Collectors.toMap(task -> task.id, task -> task.output, (first, ignored) -> first,
                        LinkedHashMap::new));
        for (WorkflowTaskState task : ready) {
            task.status = "RUNNING";
            addEvent(state, "orchestrator", "TASK_STARTED", task.id,
                    "Task entered execution wave; dependencies: " + task.dependsOn);
        }
        state.status = "RUNNING";
        state.updatedAt = Instant.now();
        persist(state);

        Map<String, String> immutableUpstream = Map.copyOf(upstream);
        List<CompletableFuture<TaskExecution>> futures = ready.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> executeTask(state, task, immutableUpstream)))
                .toList();
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            markWaveFailure(state, ready, exception);
        }

        for (int index = 0; index < ready.size(); index++) {
            WorkflowTaskState task = ready.get(index);
            if ("FAILED".equals(task.status)) {
                continue;
            }
            TaskExecution result = futures.get(index).join();
            task.attempts = result.attempts();
            task.latencyMs = result.latencyMs();
            task.output = result.output();
            task.status = result.fallback() ? "SUCCEEDED_WITH_FALLBACK" : "SUCCEEDED";
            state.retryCount += Math.max(0, result.attempts() - 1);
            for (String retryDetail : result.retryDetails()) {
                addEvent(state, "orchestrator", "TASK_RETRY", task.id, retryDetail);
            }
            if (result.fallback()) {
                addEvent(state, "fallback", "FALLBACK_USED", task.id, result.failureDetail());
            }
            addEvent(state, task.agent, "TASK_SUCCEEDED", task.id,
                    result.fallback() ? "Fallback artifact generated." : "Agent artifact generated.");
        }

        refreshTaskStatuses(state);
        settleRunState(state);
        state.updatedAt = Instant.now();
        persist(state);
        return state;
    }

    @Transactional
    public synchronized WorkflowRunState approve(String id, String taskId, String reviewer, String comment,
                                                 boolean approved) {
        WorkflowRunState state = load(id);
        WorkflowTaskState task = findTask(state, taskId);
        if (!task.approvalRequired || !"WAITING_APPROVAL".equals(task.status)) {
            throw conflict("Task is not waiting for human approval");
        }
        if (reviewer == null || reviewer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reviewer is required");
        }
        task.approved = approved;
        task.approvedBy = reviewer.trim();
        if (approved) {
            task.status = "READY";
            state.status = "READY";
            addEvent(state, reviewer.trim(), "APPROVAL_GRANTED", taskId,
                    blankAsDefault(comment, "High-impact task approved."));
        } else {
            task.status = "SKIPPED";
            state.status = "REJECTED";
            state.stopReason = "Approval rejected for " + taskId;
            state.completedAt = Instant.now();
            state.endToEndLatencyMs = elapsedMillis(state.createdAt, state.completedAt);
            state.tasks.stream().filter(other -> "BLOCKED".equals(other.status)).forEach(other -> other.status = "SKIPPED");
            addEvent(state, reviewer.trim(), "APPROVAL_REJECTED", taskId,
                    blankAsDefault(comment, "Run stopped because the high-impact action was rejected."));
        }
        state.updatedAt = Instant.now();
        persist(state);
        return state;
    }

    @Transactional
    public synchronized WorkflowRunState safeStop(String id, String actor, String reason) {
        WorkflowRunState state = load(id);
        if (TERMINAL_RUN_STATES.contains(state.status)) {
            throw conflict("A terminal run cannot be safe-stopped");
        }
        state.status = "STOPPED";
        state.stopReason = blankAsDefault(reason, "Safe stop requested by reviewer.");
        if (state.issueDetectedAt == null) {
            state.issueDetectedAt = Instant.now();
        }
        state.updatedAt = Instant.now();
        addEvent(state, blankAsDefault(actor, "human"), "SAFE_STOP", null, state.stopReason);
        persist(state);
        return state;
    }

    @Transactional
    public synchronized WorkflowRunState resume(String id, String actor) {
        WorkflowRunState state = load(id);
        if (!"STOPPED".equals(state.status)) {
            throw conflict("Only a safe-stopped run can be resumed");
        }
        state.status = "READY";
        state.stopReason = null;
        state.updatedAt = Instant.now();
        if (state.issueDetectedAt != null) {
            state.recoveryLatencyMs = elapsedMillis(state.issueDetectedAt, state.updatedAt);
            state.issueDetectedAt = null;
        }
        addEvent(state, blankAsDefault(actor, "human"), "RUN_RESUMED", null,
                "Reviewer resumed the workflow from its last completed wave.");
        persist(state);
        return state;
    }

    @Transactional
    public synchronized WorkflowRunState rollback(String id, String actor, String reason) {
        WorkflowRunState state = load(id);
        if ("ROLLED_BACK".equals(state.status)) {
            throw conflict("Run is already rolled back");
        }
        state.rollbackCount++;
        state.status = "ROLLED_BACK";
        state.stopReason = blankAsDefault(reason, "Generated artifacts withdrawn by reviewer.");
        state.completedAt = Instant.now();
        state.endToEndLatencyMs = elapsedMillis(state.createdAt, state.completedAt);
        if (state.issueDetectedAt != null && state.recoveryLatencyMs == null) {
            state.recoveryLatencyMs = elapsedMillis(state.issueDetectedAt, state.completedAt);
            state.issueDetectedAt = null;
        }
        state.updatedAt = state.completedAt;
        state.tasks.forEach(task -> task.status = "ROLLED_BACK");
        addEvent(state, blankAsDefault(actor, "human"), "ROLLBACK", null,
                "Artifacts were withdrawn. Original decisions remain in the audit log.");
        persist(state);
        return state;
    }

    @Transactional
    public synchronized WorkflowRunState replan(String id, String newRequirement, String actor) {
        WorkflowRunState state = load(id);
        String normalized = newRequirement == null ? "" : newRequirement.trim();
        if (normalized.isBlank() || normalized.length() > MAX_REQUIREMENT_LENGTH
                || normalized.matches(".*" + SECRET_PATTERN + ".*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Revised requirement is empty, too long, or contains a secret");
        }
        int oldRevision = state.planRevision;
        addEvent(state, blankAsDefault(actor, "human"), "REPLAN_REQUESTED", null,
                "Upstream requirement changed; invalidating outputs from plan revision " + oldRevision + ".");
        state.requirement = normalized;
        state.planRevision++;
        state.replanCount++;
        state.tasks = buildTasks();
        state.status = "READY";
        state.stopReason = null;
        state.completedAt = null;
        state.endToEndLatencyMs = null;
        state.updatedAt = Instant.now();
        addEvent(state, "orchestrator", "PLAN_REBUILT", null,
                "Rebuilt dependency graph for plan revision " + state.planRevision + ".");
        persist(state);
        return state;
    }

    @Transactional
    public synchronized WorkflowMetrics metrics() {
        List<WorkflowRunState> runs = list();
        long completed = runs.stream().filter(run -> "COMPLETED".equals(run.status)).count();
        long failed = runs.stream().filter(run -> "FAILED".equals(run.status)).count();
        long rolledBack = runs.stream().filter(run -> "ROLLED_BACK".equals(run.status)).count();
        long terminal = completed + failed + rolledBack
                + runs.stream().filter(run -> "REJECTED".equals(run.status)).count();
        double successRate = terminal == 0 ? 0 : (completed * 100.0) / terminal;
        double averageLatency = runs.stream().filter(run -> run.endToEndLatencyMs != null)
                .mapToLong(run -> run.endToEndLatencyMs).average().orElse(0);
        long rollbackCount = runs.stream().mapToInt(run -> run.rollbackCount).sum();
        long retriedRuns = runs.stream().filter(run -> run.retryCount > 0).count();
        long meanTimeToRecovery = Math.round(runs.stream().filter(run -> run.recoveryLatencyMs != null)
            .mapToLong(run -> run.recoveryLatencyMs).average().orElse(0));
        return new WorkflowMetrics(runs.size(), completed, failed, successRate,
            runs.stream().mapToInt(run -> run.retryCount).sum(),
            runs.isEmpty() ? 0 : (retriedRuns * 100.0) / runs.size(), rollbackCount,
            runs.isEmpty() ? 0 : (rollbackCount * 100.0) / runs.size(),
            runs.stream().mapToInt(run -> run.replanCount).sum(), meanTimeToRecovery,
            Math.round(averageLatency));
    }

    private TaskExecution executeTask(WorkflowRunState state, WorkflowTaskState task,
                                      Map<String, String> upstreamArtifacts) {
        long started = System.nanoTime();
        List<String> retryDetails = new ArrayList<>();
        String failure = null;
        for (int attempt = 1; attempt <= task.maxAttempts; attempt++) {
            try {
                String output = agent.produce(state.scenario, state.requirement, task.id, attempt,
                        state.demoFault, upstreamArtifacts);
                return new TaskExecution(output, attempt, false, null, retryDetails,
                        Duration.ofNanos(System.nanoTime() - started).toMillis());
            } catch (Exception exception) {
                failure = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                if (attempt < task.maxAttempts) {
                    retryDetails.add("Attempt " + attempt + " failed: " + failure + "; retry "
                            + (attempt + 1) + " of " + task.maxAttempts + ".");
                }
            }
        }
        if ("documentation".equals(task.id)) {
            return new TaskExecution(agent.fallback(task.id, state.requirement, upstreamArtifacts),
                    task.maxAttempts, true, failure, retryDetails,
                    Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
        throw new CompletionException(new IllegalStateException(
                "Task " + task.id + " failed after " + task.maxAttempts + " attempts: " + failure));
    }

    private void markWaveFailure(WorkflowRunState state, List<WorkflowTaskState> ready, Exception exception) {
        String detail = exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage();
        for (WorkflowTaskState task : ready) {
            if ("RUNNING".equals(task.status)) {
                task.status = "FAILED";
                task.output = null;
                addEvent(state, task.agent, "TASK_FAILED", task.id, detail);
            }
        }
        state.status = "FAILED";
        state.stopReason = detail;
        state.completedAt = Instant.now();
        state.issueDetectedAt = state.completedAt;
        state.endToEndLatencyMs = elapsedMillis(state.createdAt, state.completedAt);
    }

    private void refreshTaskStatuses(WorkflowRunState state) {
        Map<String, WorkflowTaskState> byId = state.tasks.stream()
                .collect(Collectors.toMap(task -> task.id, task -> task));
        for (WorkflowTaskState task : state.tasks) {
            if (!"BLOCKED".equals(task.status) && !"WAITING_APPROVAL".equals(task.status)) {
                continue;
            }
            boolean dependenciesReady = task.dependsOn.stream().allMatch(dependencyId -> {
                WorkflowTaskState dependency = byId.get(dependencyId);
                return dependency != null && SUCCESS_TASK_STATES.contains(dependency.status);
            });
            if (dependenciesReady) {
                task.status = task.approvalRequired && !task.approved ? "WAITING_APPROVAL" : "READY";
            }
        }
        boolean waiting = state.tasks.stream().anyMatch(task -> "WAITING_APPROVAL".equals(task.status));
        if (waiting) {
            state.status = "WAITING_APPROVAL";
        } else if (!TERMINAL_RUN_STATES.contains(state.status)) {
            state.status = "READY";
        }
    }

    private void settleRunState(WorkflowRunState state) {
        if (state.tasks.stream().anyMatch(task -> "FAILED".equals(task.status))) {
            state.status = "FAILED";
            return;
        }
        if (state.tasks.stream().anyMatch(task -> "WAITING_APPROVAL".equals(task.status))) {
            state.status = "WAITING_APPROVAL";
            return;
        }
        boolean allSuccessful = state.tasks.stream().allMatch(task -> SUCCESS_TASK_STATES.contains(task.status));
        if (allSuccessful) {
            state.status = "COMPLETED";
            state.completedAt = Instant.now();
            state.endToEndLatencyMs = elapsedMillis(state.createdAt, state.completedAt);
            addEvent(state, "orchestrator", "RUN_COMPLETED", null,
                    "All workflow stages passed their dependency and policy gates.");
        } else if ("RUNNING".equals(state.status)) {
            state.status = "READY";
        }
    }

    private List<WorkflowTaskState> buildTasks() {
        return new ArrayList<>(List.of(
                new WorkflowTaskState("requirements", "Normalize requirements", "requirements-agent",
                        "Clarify intent, assumptions, and acceptance criteria.", List.of(), "LOW", false,
                        "READY", MAX_ATTEMPTS),
                new WorkflowTaskState("architecture", "Analyze architecture", "architect-agent",
                        "Map impacted modules, APIs, data, and risks.", List.of("requirements"), "MEDIUM", false,
                        "BLOCKED", MAX_ATTEMPTS),
                new WorkflowTaskState("implementation", "Prepare implementation", "implementation-agent",
                        "Generate a reviewable change proposal; no source files are written by this demo.",
                        List.of("architecture"), "HIGH", true, "BLOCKED", MAX_ATTEMPTS),
                new WorkflowTaskState("tests", "Validate changes", "test-agent",
                        "Prepare and execute validation checks for the proposed change.",
                        List.of("implementation"), "MEDIUM", false, "BLOCKED", MAX_ATTEMPTS),
                new WorkflowTaskState("documentation", "Generate documentation", "documentation-agent",
                        "Prepare setup, decision, and limitation artifacts.",
                        List.of("implementation"), "LOW", false, "BLOCKED", MAX_ATTEMPTS),
                new WorkflowTaskState("release", "Release readiness", "release-agent",
                        "Synchronize validation and documentation before release sign-off.",
                        List.of("tests", "documentation"), "HIGH", true, "BLOCKED", MAX_ATTEMPTS)));
    }

    private WorkflowTaskState findTask(WorkflowRunState state, String taskId) {
        return state.tasks.stream().filter(task -> task.id.equals(taskId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown task: " + taskId));
    }

    private WorkflowRunState load(String id) {
        OrchestrationRunRecord record = repository.findById(Objects.requireNonNull(id, "run id"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown workflow run: " + id));
        return deserialize(record.getSnapshotJson());
    }

    private void persist(WorkflowRunState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            String id = Objects.requireNonNull(state.id, "workflow state id");
            OrchestrationRunRecord record = repository.findById(id).orElseGet(() ->
                new OrchestrationRunRecord(id, state.scenario, state.status, json,
                            state.createdAt, state.updatedAt));
            record.update(state.scenario, state.status, json, state.updatedAt);
            repository.saveAndFlush(record);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize workflow state", exception);
        }
    }

    private WorkflowRunState deserialize(String json) {
        try {
            return objectMapper.readValue(json, WorkflowRunState.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read workflow state", exception);
        }
    }

    private void addEvent(WorkflowRunState state, String actor, String type, String taskId, String detail) {
        state.events.add(new WorkflowEvent(state.events.size() + 1L, Instant.now(), state.planRevision,
                actor, type, taskId, detail));
    }

    private String normalizeScenario(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario is required");
        }
        String normalized = value.trim().toUpperCase();
        if (!Set.of("GREENFIELD", "BROWNFIELD", "AMBIGUOUS").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Scenario must be GREENFIELD, BROWNFIELD, or AMBIGUOUS");
        }
        return normalized;
    }

    private String normalizeFault(String value) {
        String normalized = value == null || value.isBlank() ? "NONE" : value.trim().toUpperCase();
        if (!DEMO_FAULTS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported demo fault mode");
        }
        return normalized;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String blankAsDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private long elapsedMillis(Instant start, Instant end) {
        return Math.max(0, Duration.between(start, end).toMillis());
    }

    private record TaskExecution(String output, int attempts, boolean fallback, String failureDetail,
                                 List<String> retryDetails, long latencyMs) {
    }
}