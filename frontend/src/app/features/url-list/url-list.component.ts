import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UrlApiService } from '../../core/url-api.service';
import { UrlSummary } from '../../core/models';

@Component({
  selector: 'app-url-list',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './url-list.component.html',
  styleUrl: './url-list.component.css',
})
export class UrlListComponent implements OnInit {
  readonly urls = signal<UrlSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  constructor(private readonly urlApi: UrlApiService) {}

  ngOnInit(): void {
    this.urlApi.listMine().subscribe({
      next: (urls) => {
        this.urls.set(urls);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load your links. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
