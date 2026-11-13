import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

/**
 * Example Express Rest API endpoints can be defined here.
 * Uncomment and define endpoints as necessary.
 *
 * Example:
 * ```ts
 * app.get('/api/{*splat}', (req, res) => {
 *   // Handle API request
 * });
 * ```
 */

/**
 * Proxy API, OAuth and media requests to the Spring Boot backend so the
 * browser remains same-origin (cookies and CSRF work without CORS exceptions).
 */
const apiProxyTarget = process.env['API_PROXY_TARGET'] ?? 'http://localhost:38133';
app.use(createProxyMiddleware({
  target: apiProxyTarget,
  changeOrigin: true,
  // Keep the browser's original Host in X-Forwarded-Host. Spring Security uses
  // it (server.forward-headers-strategy=framework) to build the OAuth redirect
  // URI, so the callback and the post-login redirect stay on the frontend origin.
  xfwd: true,
  // Plain prefixes on purpose: http-proxy-middleware v4 rejects an array that
  // mixes plain paths and globs. `/swagger-ui` also covers `/swagger-ui.html`.
  pathFilter: ['/api', '/oauth2', '/login/oauth2', '/media', '/swagger-ui', '/v3/api-docs'],
}));

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
