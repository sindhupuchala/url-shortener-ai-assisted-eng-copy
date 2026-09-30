package com.urlshortener.orchestration;

import java.util.ArrayList;
import java.util.List;

public class WorkflowTaskState {
    public String id;
    public String title;
    public String agent;
    public String description;
    public List<String> dependsOn = new ArrayList<>();
    public String risk;
    public boolean approvalRequired;
    public boolean approved;
    public String approvedBy;
    public String status;
    public int attempts;
    public int maxAttempts;
    public long latencyMs;
    public String output;

    public WorkflowTaskState() {
    }

    public WorkflowTaskState(String id, String title, String agent, String description,
                             List<String> dependsOn, String risk, boolean approvalRequired,
                             String status, int maxAttempts) {
        this.id = id;
        this.title = title;
        this.agent = agent;
        this.description = description;
        this.dependsOn = new ArrayList<>(dependsOn);
        this.risk = risk;
        this.approvalRequired = approvalRequired;
        this.status = status;
        this.maxAttempts = maxAttempts;
    }
}