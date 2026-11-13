# Development image for the Stitch & Style Angular app.
# Angular 22 dev server (Vite) runs with `ng serve`; the source tree is
# bind-mounted by docker-compose for live reload.
FROM node:24-bookworm-slim

WORKDIR /app

ENV NG_CLI_ANALYTICS=false \
    NODE_ENV=development

# Install dependencies first so the layer is cached until the lockfile changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

EXPOSE 4200

# Angular 22 rejects requests whose Host is not allow-listed; the boolean
# --allowed-hosts flag permits the .local hostnames terminated by Nginx.
# --poll makes bind-mounted file watching reliable on macOS/Windows/Linux.
# proxy.docker.conf.json points the API proxy at the Spring backend on the host
# (host.docker.internal) instead of the container's own localhost.
CMD ["npx", "ng", "serve", "--host", "0.0.0.0", "--port", "4200", "--poll", "1000", \
     "--allowed-hosts", "--proxy-config", "proxy.docker.conf.json"]
