import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UrlApiService } from '../../core/url-api.service';
import { ApiErrorResponse, UrlResponse } from '../../core/models';

@Component({
  selector: 'app-create-url',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './create-url.component.html',
  styleUrl: './create-url.component.css',
})
export class CreateUrlComponent {
  originalUrl = '';
  customAlias = '';
  expiresAt = '';

  readonly result = signal<UrlResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly copied = signal(false);

  constructor(private readonly urlApi: UrlApiService) {}

  submit(): void {
    this.errorMessage.set(null);
    this.result.set(null);
    this.copied.set(false);
    this.submitting.set(true);

    this.urlApi
      .create({
        originalUrl: this.originalUrl.trim(),
        customAlias: this.customAlias.trim() || null,
        expiresAt: this.expiresAt ? new Date(this.expiresAt).toISOString() : null,
      })
      .subscribe({
        next: (response) => {
          this.result.set(response);
          this.submitting.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.errorMessage.set(this.extractMessage(err));
          this.submitting.set(false);
        },
      });
  }

  copyToClipboard(): void {
    const shortUrl = this.result()?.shortUrl;
    if (!shortUrl) {
      return;
    }
    navigator.clipboard.writeText(shortUrl).then(() => {
      this.copied.set(true);
    });
  }

  private extractMessage(err: HttpErrorResponse): string {
    const body = err.error as ApiErrorResponse | undefined;
    if (body?.details?.length) {
      return body.details.join('; ');
    }
    return body?.message ?? 'Something went wrong. Please try again.';
  }
}
