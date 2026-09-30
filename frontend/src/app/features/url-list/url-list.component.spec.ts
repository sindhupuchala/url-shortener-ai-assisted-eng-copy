import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { UrlListComponent } from './url-list.component';
import { UrlSummary } from '../../core/models';

describe('UrlListComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UrlListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders the URLs returned by the API', () => {
    const fixture = TestBed.createComponent(UrlListComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/urls');
    expect(req.request.method).toBe('GET');

    const summaries: UrlSummary[] = [
      {
        code: 'abc123',
        shortUrl: 'http://localhost:8080/abc123',
        originalUrl: 'https://example.com/a',
        createdAt: new Date().toISOString(),
        expiresAt: null,
        totalClicks: 5,
      },
    ];
    req.flush(summaries);
    fixture.detectChanges();

    expect(fixture.componentInstance.urls()).toEqual(summaries);
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('surfaces an error message when the request fails', () => {
    const fixture = TestBed.createComponent(UrlListComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne('http://localhost:8080/api/urls');
    req.error(new ProgressEvent('network error'));

    expect(fixture.componentInstance.errorMessage()).toContain('Could not load');
  });
});
