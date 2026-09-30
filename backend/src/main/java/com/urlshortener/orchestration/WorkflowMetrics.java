package com.urlshortener.orchestration;

public record WorkflowMetrics(
        long totalRuns,
        long completedRuns,
        long failedRuns,
        double successRatePercent,
        long retryCount,
        double retryFrequencyPercent,
        long rollbackCount,
        double rollbackFrequencyPercent,
        long replanCount,
        long meanTimeToRecoveryMs,
        long averageEndToEndLatencyMs) {
}