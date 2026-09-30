import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UrlApiService } from '../../core/url-api.service';
import { UrlStats } from '../../core/models';

@Component({
  selector: 'app-url-stats',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './url-stats.component.html',
  styleUrl: './url-stats.component.css',
})
export class UrlStatsComponent implements OnInit {
  readonly stats = signal<UrlStats | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly maxDailyClicks = signal(1);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly urlApi: UrlApiService,
  ) {}

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('code');
    if (!code) {
      this.errorMessage.set('Missing short code.');
      this.loading.set(false);
      return;
    }
    this.urlApi.getStats(code).subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.maxDailyClicks.set(Math.max(1, ...stats.dailyClicks.map((d) => d.clicks)));
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Could not load stats for this link.');
        this.loading.set(false);
      },
    });
  }

  barWidth(clicks: number): string {
    return `${Math.round((clicks / this.maxDailyClicks()) * 100)}%`;
  }
}
