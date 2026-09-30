package com.urlshortener.orchestration;

import java.util.Map;

public interface WorkflowAgent {
    String produce(String scenario, String requirement, String taskId, int attempt,
                   String demoFault, Map<String, String> upstreamArtifacts) throws Exception;

    String fallback(String taskId, String requirement, Map<String, String> upstreamArtifacts);
}