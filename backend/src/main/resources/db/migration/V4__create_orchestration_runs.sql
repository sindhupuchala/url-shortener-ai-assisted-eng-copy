CREATE TABLE orchestration_runs (
    id VARCHAR(36) PRIMARY KEY,
    scenario VARCHAR(24) NOT NULL,
    run_status VARCHAR(32) NOT NULL,
    snapshot_json CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_orchestration_runs_created_at ON orchestration_runs(created_at);