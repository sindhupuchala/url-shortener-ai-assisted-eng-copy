import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import { CreateUrlRequest, UrlResponse, UrlStats, UrlSummary } from './models';

@Injectable({ providedIn: 'root' })
export class UrlApiService {
  constructor(private readonly http: HttpClient) {}

  create(request: CreateUrlRequest): Observable<UrlResponse> {
    return this.http.post<UrlResponse>(`${API_BASE_URL}/api/urls`, request);
  }

  getMetadata(code: string): Observable<UrlResponse> {
    return this.http.get<UrlResponse>(`${API_BASE_URL}/api/urls/${code}`);
  }

  listMine(): Observable<UrlSummary[]> {
    return this.http.get<UrlSummary[]>(`${API_BASE_URL}/api/urls`);
  }

  getStats(code: string): Observable<UrlStats> {
    return this.http.get<UrlStats>(`${API_BASE_URL}/api/urls/${code}/stats`);
  }
}
