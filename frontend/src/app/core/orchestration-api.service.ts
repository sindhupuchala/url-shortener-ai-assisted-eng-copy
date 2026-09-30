import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import {
  CreateWorkflowRequest,
  WorkflowAction,
  WorkflowMetrics,
  WorkflowRun,
} from './orchestration.models';

@Injectable({ providedIn: 'root' })
export class OrchestrationApiService {
  private readonly baseUrl = `${API_BASE_URL}/api/orchestration`;

  constructor(private readonly http: HttpClient) {}

  listRuns(): Observable<WorkflowRun[]> {
    return this.http.get<WorkflowRun[]>(`${this.baseUrl}/runs`);
  }

  createRun(request: CreateWorkflowRequest): Observable<WorkflowRun> {
    return this.http.post<WorkflowRun>(`${this.baseUrl}/runs`, request);
  }

  advance(id: string): Observable<WorkflowRun> {
    return this.http.post<WorkflowRun>(`${this.baseUrl}/runs/${id}/advance`, {});
  }

  approve(id: string, taskId: string, action: WorkflowAction): Observable<WorkflowRun> {
    return this.http.post<WorkflowRun>(`${this.baseUrl}/runs/${id}/tasks/${taskId}/approve`, action);
  }

  reject(id: string, taskId: string, action: WorkflowAction): Observable<WorkflowRun> {
    return this.http.post<WorkflowRun>(`${this.baseUrl}/runs/${id}/tasks/${taskId}/reject`, action);
  }

  safeStop(id: string, action: WorkflowAction): Observable<WorkflowRun> {
    return this.http.post<WorkflowRun>(`${this.baseUrl}/runs/${id}/safe-stop`, action);
  }

  resume(id: string, action: WorkflowAction): Observable<WorkflowRun> {
    return this.http.post<WorkflowRun>(`${this.baseUrl}/runs/${id}/resume`, action);
  }

  rollback(id: string, action: WorkflowAction): Observable<WorkflowRun> {
    return this.http.post<WorkflowRun>(`${this.baseUrl}/runs/${id}/rollback`, action);
  }

  replan(id: string, requirement: string, actor: string): Observable<WorkflowRun> {
    return this.http.post<WorkflowRun>(`${this.baseUrl}/runs/${id}/replan`, { requirement, actor });
  }

  getMetrics(): Observable<WorkflowMetrics> {
    return this.http.get<WorkflowMetrics>(`${this.baseUrl}/metrics`);
  }
}