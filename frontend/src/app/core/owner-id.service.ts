import { Injectable } from '@angular/core';

const STORAGE_KEY = 'url-shortener.owner-id';

/**
 * Lightweight client-side pseudo-identity: a random id persisted in localStorage so the
 * dashboard can scope "my URLs" without a real auth system. Documented limitation, not a
 * security boundary - see docs/ENGINEERING_SUMMARY.md.
 */
@Injectable({ providedIn: 'root' })
export class OwnerIdService {
  private readonly ownerId: string;

  constructor() {
    this.ownerId = this.loadOrCreate();
  }

  getOwnerId(): string {
    return this.ownerId;
  }

  private loadOrCreate(): string {
    const existing = localStorage.getItem(STORAGE_KEY);
    if (existing) {
      return existing;
    }
    const generated = crypto.randomUUID();
    localStorage.setItem(STORAGE_KEY, generated);
    return generated;
  }
}
