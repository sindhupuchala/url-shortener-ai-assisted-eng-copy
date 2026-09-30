package com.urlshortener.orchestration;

import java.time.Instant;

public record WorkflowEvent(
        long sequence,
        Instant at,
        int planRevision,
        String actor,
        String type,
        String taskId,
        String detail) {
}