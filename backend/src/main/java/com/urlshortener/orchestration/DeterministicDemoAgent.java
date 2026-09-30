package com.urlshortener.orchestration;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeterministicDemoAgent implements WorkflowAgent {

    @Override
    public String produce(String scenario, String requirement, String taskId, int attempt,
                          String demoFault, Map<String, String> upstreamArtifacts) {
        if ("TRANSIENT_TEST_FAILURE".equals(demoFault) && "tests".equals(taskId) && attempt == 1) {
            throw new IllegalStateException("Injected transient test-runner failure");
        }
        if ("DOCUMENTATION_FALLBACK".equals(demoFault) && "documentation".equals(taskId)) {
            throw new IllegalStateException("Injected documentation-agent failure");
        }

        return switch (taskId) {
            case "requirements" -> "Normalized " + scenario.toLowerCase()
                    + " requirement: " + requirement.trim()
                    + "\nAssumptions and acceptance criteria are recorded for review.";
            case "architecture" -> "Architecture artifact: API boundary, persistence, and change surface."
                    + "\nContext inputs: " + upstreamArtifacts.keySet();
            case "implementation" -> "Implementation artifact prepared from the approved architecture."
                    + "\nNo repository files were modified by this demo worker.";
            case "tests" -> "Validation plan: unit, integration, regression, and negative-path checks."
                    + "\nUpstream implementation artifact: " + upstreamArtifacts.containsKey("implementation");
            case "documentation" -> "Documentation artifact: setup, architecture, trade-offs, and release notes."
                    + "\nUpstream implementation artifact: " + upstreamArtifacts.containsKey("implementation");
            case "release" -> "Release readiness: tests and documentation synchronized; human sign-off recorded.";
            default -> throw new IllegalArgumentException("Unknown workflow task: " + taskId);
        };
    }

    @Override
    public String fallback(String taskId, String requirement, Map<String, String> upstreamArtifacts) {
        return "Fallback template used for " + taskId + " after bounded retries."
                + "\nRequirement revision: " + requirement.trim()
                + "\nAvailable upstream artifacts: " + upstreamArtifacts.keySet();
    }
}