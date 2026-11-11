import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Prerender },
  { path: 'terms', renderMode: RenderMode.Prerender },
  { path: 'privacy', renderMode: RenderMode.Prerender },
  { path: 'returns', renderMode: RenderMode.Prerender },
  { path: 'delivery', renderMode: RenderMode.Prerender },
  { path: 'cart', renderMode: RenderMode.Client },
  { path: 'checkout', renderMode: RenderMode.Client },
  { path: 'account', renderMode: RenderMode.Client },
  { path: 'admin/**', renderMode: RenderMode.Client },
  { path: 'wishlist', renderMode: RenderMode.Client },
  { path: 'returns/request', renderMode: RenderMode.Client },
  { path: 'contact', renderMode: RenderMode.Client },
  { path: '**', renderMode: RenderMode.Server },
];
