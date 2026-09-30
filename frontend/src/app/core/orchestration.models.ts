export interface WorkflowTask {
  id: string;
  title: string;
  agent: string;
  description: string;
  dependsOn: string[];
  risk: string;
  approvalRequired: boolean;
  approved: boolean;
  approvedBy: string | null;
  status: string;
  attempts: number;
  maxAttempts: number;
  latencyMs: number;
  output: string | null;
}

export interface WorkflowEvent {
  sequence: number;
  at: string;
  planRevision: number;
  actor: string;
  type: string;
  taskId: string | null;
  detail: string;
}

export interface WorkflowRun {
  id: string;
  scenario: string;
  requirement: string;
  status: string;
  demoFault: string;
  stopReason: string | null;
  planRevision: number;
  retryCount: number;
  rollbackCount: number;
  replanCount: number;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  issueDetectedAt: string | null;
  recoveryLatencyMs: number | null;
  endToEndLatencyMs: number | null;
  tasks: WorkflowTask[];
  events: WorkflowEvent[];
}

export interface WorkflowMetrics {
  totalRuns: number;
  completedRuns: number;
  failedRuns: number;
  successRatePercent: number;
  retryCount: number;
  retryFrequencyPercent: number;
  rollbackCount: number;
  rollbackFrequencyPercent: number;
  replanCount: number;
  meanTimeToRecoveryMs: number;
  averageEndToEndLatencyMs: number;
}

export interface CreateWorkflowRequest {
  scenario: string;
  requirement: string;
  demoFault: string;
}

export interface WorkflowAction {
  actor: string;
  comment?: string;
}