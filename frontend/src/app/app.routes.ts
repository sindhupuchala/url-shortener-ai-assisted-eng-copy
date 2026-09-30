import { Routes } from '@angular/router';
import { CreateUrlComponent } from './features/create-url/create-url.component';
import { UrlListComponent } from './features/url-list/url-list.component';
import { UrlStatsComponent } from './features/url-stats/url-stats.component';
import { OrchestrationComponent } from './features/orchestration/orchestration.component';

export const routes: Routes = [
  { path: '', component: CreateUrlComponent },
  { path: 'urls', component: UrlListComponent },
  { path: 'stats/:code', component: UrlStatsComponent },
  { path: 'orchestrator', component: OrchestrationComponent },
  { path: '**', redirectTo: '' },
];
