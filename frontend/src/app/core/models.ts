export interface CreateUrlRequest {
  originalUrl: string;
  customAlias?: string | null;
  expiresAt?: string | null; // ISO-8601 instant
}

export interface UrlResponse {
  code: string;
  shortUrl: string;
  originalUrl: string;
  createdAt: string;
  expiresAt: string | null;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  details: string[];
}

export interface UrlSummary {
  code: string;
  shortUrl: string;
  originalUrl: string;
  createdAt: string;
  expiresAt: string | null;
  totalClicks: number;
}

export interface DailyClickCount {
  date: string;
  clicks: number;
}

export interface UrlStats {
  code: string;
  originalUrl: string;
  totalClicks: number;
  createdAt: string;
  dailyClicks: DailyClickCount[];
}
