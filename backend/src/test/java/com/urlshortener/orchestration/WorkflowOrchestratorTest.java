package com.urlshortener.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WorkflowOrchestratorTest {

    @Autowired
    private WorkflowOrchestrator orchestrator;

    @Autowired
    private OrchestrationRunRepository repository;

    @BeforeEach
    void clearRuns() {
        repository.deleteAll();
    }

    @Test
    void executesDependencyWavesWithParallelBranchesAndApprovalGates() {
        WorkflowRunState run = orchestrator.create("greenfield", "Build a URL shortener", "NONE");

        run = orchestrator.advance(run.id);
        assertEquals("SUCCEEDED", task(run, "requirements").status);
        assertEquals("READY", task(run, "architecture").status);

        run = orchestrator.advance(run.id);
        assertEquals("WAITING_APPROVAL", run.status);
        assertEquals("WAITING_APPROVAL", task(run, "implementation").status);

        run = orchestrator.approve(run.id, "implementation", "reviewer", "Plan reviewed", true);
        run = orchestrator.advance(run.id);
        assertEquals("SUCCEEDED", task(run, "implementation").status);
        assertEquals("READY", task(run, "tests").status);
        assertEquals("READY", task(run, "documentation").status);

        run = orchestrator.advance(run.id);
        assertEquals("SUCCEEDED", task(run, "tests").status);
        assertEquals("SUCCEEDED", task(run, "documentation").status);
        assertEquals("WAITING_APPROVAL", run.status);
        assertTrue(run.events.stream().anyMatch(event -> "TASK_STARTED".equals(event.type())
                && "tests".equals(event.taskId())));
        assertTrue(run.events.stream().anyMatch(event -> "TASK_STARTED".equals(event.type())
                && "documentation".equals(event.taskId())));

        run = orchestrator.approve(run.id, "release", "reviewer", "Ready to release", true);
        run = orchestrator.advance(run.id);
        assertEquals("COMPLETED", run.status);
        assertTrue(run.endToEndLatencyMs >= 0);
        assertEquals(1, orchestrator.metrics().completedRuns());
    }

    @Test
    void retriesTransientFailureAndUsesFallbackAfterBoundedDocumentationRetries() {
        WorkflowRunState retryRun = orchestrator.create("AMBIGUOUS", "Handle a traffic spike", "TRANSIENT_TEST_FAILURE");
        retryRun = advanceToImplementation(retryRun);
        retryRun = orchestrator.advance(retryRun.id);
        retryRun = orchestrator.advance(retryRun.id);

        assertEquals("SUCCEEDED", task(retryRun, "tests").status);
        assertEquals(1, retryRun.retryCount);
        assertTrue(retryRun.events.stream().anyMatch(event -> "TASK_RETRY".equals(event.type())));

        WorkflowRunState fallbackRun = orchestrator.create("BROWNFIELD", "Add analytics", "DOCUMENTATION_FALLBACK");
        fallbackRun = advanceToImplementation(fallbackRun);
        fallbackRun = orchestrator.advance(fallbackRun.id);
        fallbackRun = orchestrator.advance(fallbackRun.id);

        assertEquals("SUCCEEDED_WITH_FALLBACK", task(fallbackRun, "documentation").status);
        assertTrue(fallbackRun.events.stream().anyMatch(event -> "FALLBACK_USED".equals(event.type())));
        assertEquals(100.0, orchestrator.metrics().retryFrequencyPercent());
    }

    @Test
    void preservesDecisionLineageAcrossReplanAndSupportsSafeStopAndRollback() {
        WorkflowRunState run = orchestrator.create("GREENFIELD", "Create links", "NONE");
        run = orchestrator.advance(run.id);
        int oldEventCount = run.events.size();

        run = orchestrator.replan(run.id, "Create links with expiry", "reviewer");
        assertEquals(2, run.planRevision);
        assertEquals(1, run.replanCount);
        assertEquals(oldEventCount + 2, run.events.size());
        assertEquals("READY", task(run, "requirements").status);

        run = orchestrator.safeStop(run.id, "reviewer", "Inspecting revised acceptance criteria");
        assertEquals("STOPPED", run.status);
        String stoppedRunId = run.id;
        assertThrows(ResponseStatusException.class, () -> orchestrator.advance(stoppedRunId));

        run = orchestrator.resume(run.id, "reviewer");
        assertEquals("READY", run.status);
        assertTrue(run.recoveryLatencyMs >= 0);
        run = orchestrator.rollback(run.id, "reviewer", "Withdraw proposed artifacts");
        assertEquals("ROLLED_BACK", run.status);
        assertEquals(1, run.rollbackCount);
        assertEquals(100.0, orchestrator.metrics().rollbackFrequencyPercent());
        assertTrue(run.events.stream().anyMatch(event -> "ROLLBACK".equals(event.type())));
    }

    @Test
    void rejectsRequirementsThatContainSecretMaterial() {
        assertThrows(ResponseStatusException.class,
                () -> orchestrator.create("GREENFIELD", "Set api_key=abcdef1234567890", "NONE"));
    }

    private WorkflowRunState advanceToImplementation(WorkflowRunState run) {
        run = orchestrator.advance(run.id);
        run = orchestrator.advance(run.id);
        return orchestrator.approve(run.id, "implementation", "reviewer", "Approved for demo", true);
    }

    private WorkflowTaskState task(WorkflowRunState run, String taskId) {
        return run.tasks.stream().filter(candidate -> taskId.equals(candidate.id)).findFirst().orElseThrow();
    }
}