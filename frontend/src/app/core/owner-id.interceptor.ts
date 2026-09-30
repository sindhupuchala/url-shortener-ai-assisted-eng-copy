import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OwnerIdService } from './owner-id.service';

export const ownerIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/')) {
    return next(req);
  }
  const ownerId = inject(OwnerIdService).getOwnerId();
  return next(req.clone({ setHeaders: { 'X-Owner-Id': ownerId } }));
};
