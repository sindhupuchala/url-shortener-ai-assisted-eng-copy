import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { UrlStatsComponent } from './url-stats.component';
import { UrlStats } from '../../core/models';

describe('UrlStatsComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UrlStatsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ code: 'abc123' }) } },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and displays stats for the routed code', () => {
    const fixture = TestBed.createComponent(UrlStatsComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/urls/abc123/stats');
    const stats: UrlStats = {
      code: 'abc123',
      originalUrl: 'https://example.com/a',
      totalClicks: 4,
      createdAt: new Date().toISOString(),
      dailyClicks: [
        { date: '2026-08-24', clicks: 1 },
        { date: '2026-08-25', clicks: 3 },
      ],
    };
    req.flush(stats);

    expect(fixture.componentInstance.stats()?.totalClicks).toBe(4);
    expect(fixture.componentInstance.maxDailyClicks()).toBe(3);
    expect(fixture.componentInstance.barWidth(3)).toBe('100%');
  });
});
