import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CreateUrlComponent } from './create-url.component';
import { UrlResponse } from '../../core/models';

describe('CreateUrlComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateUrlComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('submits the form and shows the resulting short URL', () => {
    const fixture = TestBed.createComponent(CreateUrlComponent);
    const component = fixture.componentInstance;
    component.originalUrl = 'https://example.com/long/path';
    fixture.detectChanges();

    component.submit();

    const req = httpMock.expectOne('http://localhost:8080/api/urls');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.originalUrl).toBe('https://example.com/long/path');

    const response: UrlResponse = {
      code: 'abc123',
      shortUrl: 'http://localhost:8080/abc123',
      originalUrl: 'https://example.com/long/path',
      createdAt: new Date().toISOString(),
      expiresAt: null,
    };
    req.flush(response);

    expect(component.result()?.shortUrl).toBe('http://localhost:8080/abc123');
    expect(component.errorMessage()).toBeNull();
  });

  it('surfaces the server validation message on error', () => {
    const fixture = TestBed.createComponent(CreateUrlComponent);
    const component = fixture.componentInstance;
    component.originalUrl = 'not-a-url';
    fixture.detectChanges();

    component.submit();

    const req = httpMock.expectOne('http://localhost:8080/api/urls');
    req.flush(
      { timestamp: new Date().toISOString(), status: 400, error: 'Bad Request', message: 'Validation failed', details: ['originalUrl: must be a well-formed absolute URL'] },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(component.errorMessage()).toContain('must be a well-formed absolute URL');
    expect(component.result()).toBeNull();
  });
});
