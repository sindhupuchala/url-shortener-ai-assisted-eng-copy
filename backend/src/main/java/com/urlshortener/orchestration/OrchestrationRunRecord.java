package com.urlshortener.orchestration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "orchestration_runs")
public class OrchestrationRunRecord {

    @Id
    private String id;

    @Column(nullable = false, length = 24)
    private String scenario;

    @Column(name = "run_status", nullable = false, length = 32)
    private String runStatus;

    @Lob
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "CLOB")
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrchestrationRunRecord() {
    }

    public OrchestrationRunRecord(String id, String scenario, String runStatus, String snapshotJson,
                                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.scenario = scenario;
        this.runStatus = runStatus;
        this.snapshotJson = snapshotJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getScenario() {
        return scenario;
    }

    public String getRunStatus() {
        return runStatus;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String scenario, String runStatus, String snapshotJson, Instant updatedAt) {
        this.scenario = scenario;
        this.runStatus = runStatus;
        this.snapshotJson = snapshotJson;
        this.updatedAt = updatedAt;
    }
}