package com.urlshortener.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class WorkflowRunState {
    public String id;
    public String scenario;
    public String requirement;
    public String status;
    public String demoFault;
    public String stopReason;
    public int planRevision;
    public int retryCount;
    public int rollbackCount;
    public int replanCount;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant completedAt;
    public Instant issueDetectedAt;
    public Long recoveryLatencyMs;
    public Long endToEndLatencyMs;
    public List<WorkflowTaskState> tasks = new ArrayList<>();
    public List<WorkflowEvent> events = new ArrayList<>();

    public WorkflowRunState() {
    }
}