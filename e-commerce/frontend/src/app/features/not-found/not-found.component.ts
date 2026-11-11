import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template: `
    <section class="page-container py-20 text-center">
      <p class="text-7xl font-black text-primary">404</p>
      <h1 class="mt-3 text-3xl font-black">Page not found</h1>
      <p class="mt-2 text-sm opacity-60">The page may have moved, or the link is no longer available.</p>
      <a routerLink="/" class="btn btn-primary mt-6">Back to the storefront</a>
    </section>
  `,
})
export class NotFoundComponent {}
