import { TestBed } from '@angular/core/testing';
import { OwnerIdService } from './owner-id.service';

describe('OwnerIdService', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('generates and persists an owner id on first use', () => {
    const service = TestBed.inject(OwnerIdService);
    const id = service.getOwnerId();

    expect(id).toBeTruthy();
    expect(window.localStorage.getItem('url-shortener.owner-id')).toBe(id);
  });

  it('reuses the id already stored in window.localStorage', () => {
    window.localStorage.setItem('url-shortener.owner-id', 'existing-owner-id');

    const service = TestBed.inject(OwnerIdService);

    expect(service.getOwnerId()).toBe('existing-owner-id');
  });
});
