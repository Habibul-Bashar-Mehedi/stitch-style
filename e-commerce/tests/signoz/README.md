# SigNoz — traces/metrics/logs for Stitch & Style

Self-hosted [SigNoz](https://signoz.io) (OpenTelemetry APM) plus a runner that
starts the Spring backend with the OpenTelemetry Java agent, so load tests can
be inspected as traces instead of only as latency numbers.

`casting.yaml` is the Foundry source of truth; `pours/` is generated and
git-ignored.

## Start SigNoz

```bash
cd e-commerce/tests/signoz

foundryctl gauge -f casting.yaml     # validate docker
foundryctl forge -f casting.yaml     # generate pours/deployment/compose.yaml
cd pours/deployment && docker compose up -d
```

Open **http://localhost:8080** and **create the first admin account**. This is
not optional: the collector registers with the server over OpAMP, and until an
organization exists the server rejects it with
`cannot create agent without orgId`, so the OTLP receivers never bind and the
agent's exports fail with `Failed to export N spans`.

| Port | Service |
|---|---|
| `8080` | SigNoz UI |
| `4317` | OTLP gRPC ingestion |
| `4318` | OTLP HTTP ingestion |

Verify ingestion after onboarding:

```bash
docker exec signoz-telemetrystore-clickhouse-0-0 clickhouse-client \
  --query "SELECT serviceName, count() FROM signoz_traces.distributed_signoz_index_v3 GROUP BY serviceName"
```

Stop it with `docker compose -f pours/deployment/compose.yaml down`
(add `-v` to wipe telemetry).

## Instrument the backend

Because Google OIDC cannot be automated, the runner also enables dev-tools and
starts on **:38134** so it does not disturb an IntelliJ run on :38133:

```bash
cd e-commerce/tests/signoz
./run-backend-otel.sh
```

It exports `OTEL_SERVICE_NAME=stitchstyle-backend` to
`http://localhost:4317`. The agent instruments Spring MVC, JDBC (SQL + timings),
HTTP clients, scheduled jobs and JVM metrics — no code changes.

To instrument your normal IntelliJ run instead, add this VM option:

```
-javaagent:/absolute/path/to/e-commerce/tests/signoz/opentelemetry-javaagent.jar
```

and these environment variables:

```
OTEL_SERVICE_NAME=stitchstyle-backend
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
OTEL_EXPORTER_OTLP_PROTOCOL=grpc
OTEL_RESOURCE_ATTRIBUTES=deployment.environment=local
```

## Load-test against it

```bash
# k6
cd e-commerce/tests/k6
BASE_URL=http://localhost:38134 VUS_MAX=50 RAMP=10 HOLD=20 ./run.sh orders

# or Gatling
cd e-commerce/tests/gatling
BASE_URL=http://localhost:38134 VUS=50 RAMP=10 HOLD=20 \
  ./run.sh com.stitchstyle.gatling.simulations.CheckoutSimulation
```

Then in SigNoz: **Services → stitchstyle-backend → Traces**. Useful filters:

- `http.route` — group by endpoint (`/api/v1/public/orders`, `/me/cart`, …)
- `db.system = postgresql` — SQL spans and slow queries under load
- `service.name = stitchstyle-backend` + `hasError = true` — failed requests
- **Dashboards → JVM** — heap, GC, threads (agent-provided)

## Resource warning

A single-node SigNoz needs **~4 GB RAM**. This laptop has ~1 GB free, so the
stack swaps. Stop Grafana/InfluxDB while using SigNoz:

```bash
cd e-commerce/tests/k6 && docker compose stop grafana influxdb
```

and restart them later with `docker compose start grafana influxdb`.
