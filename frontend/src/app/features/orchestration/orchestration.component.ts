import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { OrchestrationApiService } from '../../core/orchestration-api.service';
import {
  CreateWorkflowRequest,
  WorkflowMetrics,
  WorkflowRun,
  WorkflowTask,
} from '../../core/orchestration.models';

@Component({
  selector: 'app-orchestration',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  templateUrl: './orchestration.component.html',
  styleUrl: './orchestration.component.css',
})
export class OrchestrationComponent implements OnInit {
  readonly runs = signal<WorkflowRun[]>([]);
  readonly selectedRun = signal<WorkflowRun | null>(null);
  readonly metrics = signal<WorkflowMetrics | null>(null);
  readonly busy = signal(false);
  readonly errorMessage = signal<string | null>(null);

  scenario = 'GREENFIELD';
  requirement = 'Build a URL shortener with analytics and safe redirects';
  demoFault = 'NONE';
  reviewer = 'Sindhu Puchala';
  revisedRequirement = '';

  constructor(private readonly api: OrchestrationApiService) {}

  ngOnInit(): void {
    this.refreshRuns();
    this.refreshMetrics();
  }

  createRun(): void {
    if (this.busy()) return;
    const request: CreateWorkflowRequest = {
      scenario: this.scenario,
      requirement: this.requirement,
      demoFault: this.demoFault,
    };
    this.runRequest(this.api.createRun(request));
  }

  selectRun(run: WorkflowRun): void {
    this.selectedRun.set(run);
    this.errorMessage.set(null);
  }

  advance(): void {
    const run = this.selectedRun();
    if (run) this.runRequest(this.api.advance(run.id));
  }

  review(task: WorkflowTask, approved: boolean): void {
    const run = this.selectedRun();
    if (!run) return;
    const action = {
      actor: this.reviewer,
      comment: approved ? 'Approved in workflow console.' : 'Rejected in workflow console.',
    };
    const request = approved
      ? this.api.approve(run.id, task.id, action)
      : this.api.reject(run.id, task.id, action);
    this.runRequest(request);
  }

  safeStop(): void {
    const run = this.selectedRun();
    if (run) this.runRequest(this.api.safeStop(run.id, { actor: this.reviewer, comment: 'Reviewer requested safe stop.' }));
  }

  resume(): void {
    const run = this.selectedRun();
    if (run) this.runRequest(this.api.resume(run.id, { actor: this.reviewer }));
  }

  rollback(): void {
    const run = this.selectedRun();
    if (run && window.confirm('Withdraw this run’s generated artifacts? The audit trail will remain.')) {
      this.runRequest(this.api.rollback(run.id, { actor: this.reviewer, comment: 'Reviewer withdrew generated artifacts.' }));
    }
  }

  replan(): void {
    const run = this.selectedRun();
    if (!run || !this.revisedRequirement.trim()) return;
    this.runRequest(this.api.replan(run.id, this.revisedRequirement, this.reviewer));
    this.revisedRequirement = '';
  }

  isTerminal(run: WorkflowRun): boolean {
    return ['COMPLETED', 'FAILED', 'REJECTED', 'ROLLED_BACK'].includes(run.status);
  }

  statusClass(status: string): string {
    return status.toLowerCase().replaceAll('_', '-');
  }

  getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as { message?: string } | null;
      return body?.message ?? `Request failed (${error.status}).`;
    }
    return 'The workflow request failed. Check that the backend is running.';
  }

  private runRequest(request: import('rxjs').Observable<WorkflowRun>): void {
    this.busy.set(true);
    this.errorMessage.set(null);
    request.subscribe({
      next: (run) => {
        this.acceptRun(run);
        this.refreshRuns();
        this.refreshMetrics();
        this.busy.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(this.getErrorMessage(error));
        this.busy.set(false);
      },
    });
  }

  private acceptRun(run: WorkflowRun): void {
    this.selectedRun.set(run);
  }

  private refreshRuns(): void {
    this.api.listRuns().subscribe({
      next: (runs) => {
        this.runs.set(runs);
        const selectedId = this.selectedRun()?.id;
        const latest = selectedId ? runs.find((run) => run.id === selectedId) : runs[0];
        if (latest) this.selectedRun.set(latest);
      },
      error: () => this.errorMessage.set('Could not load workflow run history.'),
    });
  }

  private refreshMetrics(): void {
    this.api.getMetrics().subscribe({
      next: (metrics) => this.metrics.set(metrics),
      error: () => this.metrics.set(null),
    });
  }
}