# Stitch & Style — Docker + Nginx local entry point

Run the Angular dev server behind Nginx so the shop is reachable at the port-less
URLs below, with live reload and HMR intact.

```
http://localhost
http://stitchandstyle.local
http://stitch-and-style.local
http://stitchstyle.local
```

## Start / stop

```bash
cd e-commerce/frontend

docker compose up -d --build     # first run builds the image and npm ci
docker compose ps                # stitchstyle-angular (healthy), stitchstyle-nginx (80)
docker compose logs -f nginx     # access log
docker compose logs -f angular-app

docker compose down              # stop (add -v to drop the node_modules volume)
```

Compose project name: **`stitchstyle`**. Containers: `stitchstyle-angular`
(internal only) and `stitchstyle-nginx` (`0.0.0.0:80`). They share the
`stitchstyle` bridge network. This is separate from `e-commerce/docker-compose.yml`
(Postgres/Redis/MinIO) — run each from its own directory.

## Files

| File | Purpose |
|---|---|
| `Dockerfile` | Node 24 dev image: `npm ci`, `ng serve --host 0.0.0.0 --port 4200 --poll 1000` |
| `nginx.conf` | default server on :80, all four names, `proxy_pass http://angular-app:4200`, WebSocket upgrade headers |
| `docker-compose.yml` | `angular-app` + `nginx`, shared network, port 80 |
| `proxy.docker.conf.json` | Angular dev-server API proxy → `host.docker.internal:38133` |
| `.dockerignore` | keeps the build context small |

The source tree is bind-mounted (`./:/app`) and `--poll 1000` makes file watching
reliable inside Docker. `node_modules` and `.angular` are container volumes so the
host folders do not shadow them.

## Hostnames

Nginx listens with `default_server`, so **`http://localhost` works without editing
anything**. The named hosts need one `/etc/hosts` line (the helper script writes it
idempotently and verifies resolution + Nginx):

```bash
cd e-commerce/frontend
bash scripts/add-local-hosts.sh        # asks for sudo once
```

It maps all of these to `127.0.0.1`:

```
stitchandstyle.com   www.stitchandstyle.com
stichandstyle.com    stich-style.com
stichstyle.com       www.stichstyle.com
stitchstyle.com      www.stitchstyle.com
stitchandstyle.local stitch-and-style.local stitchstyle.local
```

Manual equivalent:

```bash
echo "127.0.0.1 stitchandstyle.com www.stitchandstyle.com stichandstyle.com stich-style.com stichstyle.com stitchstyle.com www.stitchstyle.com stitchandstyle.local stitch-and-style.local stitchstyle.local" | sudo tee -a /etc/hosts
```

Windows (Administrator PowerShell):

```powershell
Add-Content "$env:WINDIR\System32\drivers\etc\hosts" "`n127.0.0.1 stitchandstyle.com www.stitchandstyle.com stichandstyle.com stich-style.com stichstyle.com stitchstyle.com www.stitchstyle.com stitchandstyle.local stitch-and-style.local stitchstyle.local"
```

Then open `http://stitchandstyle.com` (use the `http://` prefix the first time;
browsers may try HTTPS first for a `.com`).

> `&` and spaces are illegal in hostnames. `stich&style.com` can never resolve —
> browsers treat it as a search. Use `stichstyle.com`, `stich-style.com` or
> `stichandstyle.com` instead.

> `stitchandstyle.com` and `stitchstyle.com` are real registered domains; the
> hosts entry overrides them **only on this machine** so they point at the local
> app.

## Type "stitch and style" and press Enter

Nginx exposes a tiny redirector at `http://localhost/go?q=%s`:

- `stitch and style`, `stitch&style`, `stitchandstyle`, `stitchstyle` (and the
  `stich…` spellings) → **302 to `http://localhost/`**
- every other query → **302 to Google**

Make it the browser's default search engine:

**Chrome / Edge**
1. Open `chrome://settings/searchEngines` (`edge://settings/searchEngines`)
2. **Site search → Add**
   - Search engine: `Stitch & Style`
   - Shortcut: `stitch` (any word; it is not used in default mode)
   - URL: `http://localhost/go?q=%s`
3. Three-dot menu on the new entry → **Make default**

Now typing `stitch and style`, `stitch&style` or `stitchandstyle` in the address
bar and pressing Enter opens the shop. Everything else is still searched on
Google (through the local redirector).

> Caveat: while this is the default engine, **all** searches pass through local
> Nginx. Stop the stack (`docker compose down` / the host-network Nginx) and
> searching breaks until you switch the default back to Google. If Chrome refuses
> an `http://localhost` default engine, keep the entry and use its shortcut:
> type `stitch` + Space + Enter.

**Firefox** — open `http://localhost/opensearch.xml` and add it, or bookmark the
shop and set the bookmark keyword to `ss`.

## Two ways to run

**A. Full Docker (requested architecture)** — Nginx + the Angular dev server in
containers. It needs the host firewall to allow container→host traffic to the
Spring backend:

```bash
docker compose up -d --build
sudo ufw allow from 172.16.0.0/12 to any port 38133 proto tcp
```

**B. No-sudo fallback (currently running)** — only Nginx runs, with host
networking, and proxies to the `npm start` dev server already on the host:

```bash
npm start                        # terminal 1 (ng serve on :4200)
docker compose down              # stop mode A if running
docker compose -f docker-compose.host.yml up -d
# http://localhost now serves the app, API and media
```

Linux only (Docker Desktop does not support host networking). `nginx.host.conf`
sets `Host localhost:4200` because the host dev server was started without
`--allowed-hosts`.

## The Spring backend must be reachable

The storefront fetches its data from the backend you run on the host at
`http://localhost:38133`:

```bash
cd e-commerce/backend && ./gradlew bootRun
```

`proxy.docker.conf.json` targets `host.docker.internal:38133`. On Linux, if Docker
containers cannot reach host-local ports (a host firewall such as UFW blocks
`docker0`→host INPUT), the UI loads but API calls hang. Allow the Docker bridge
subnet once:

```bash
sudo ufw allow from 172.16.0.0/12 to any port 38133 proto tcp
```

(or `sudo ufw allow in on docker0 to any port 38133 proto tcp`). If you cannot
change the firewall, run the backend inside Docker and point the proxy at its
service name instead.

Google sign-in additionally needs the OAuth redirect URIs registered for the
new origin, e.g. `http://localhost/login/oauth2/code/google`,
`http://stitchandstyle.local/login/oauth2/code/google`. Public browsing, cart,
checkout and tracking need no sign-in.

## Checks

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost/
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: stitchstyle.local' http://127.0.0.1/
curl -s http://localhost/api/v1/public/home | head -c 120    # JSON once the backend is reachable
```

HMR: open the site, edit a component — `docker compose logs -f angular-app` shows
`Changes detected. Rebuilding...`, and the browser updates without a reload. The
WebSocket is proxied by Nginx (`Upgrade`/`Connection` headers) and stays connected.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Port 80 already in use | Stop the conflicting service (`sudo ss -ltnp \| grep :80`) or change `ports` in compose |
| `angular-app` unhealthy | `docker compose logs angular-app`; usually the dev server failed to compile |
| Page loads, data empty / requests hang | Host backend not running, or UFW blocks Docker→host (see above) |
| `Bad Request: Header "host" ... not allowed` | Angular 22 host allow-list — the compose command passes `--allowed-hosts` |
| HMR not connecting | Ensure the `Upgrade`/`Connection` headers stay **inside** `location /` (Nginx stops inheriting `proxy_set_header` once a location defines one) |
| Password/keychain prompts on macOS | The repo must live in a Docker-shared path |
