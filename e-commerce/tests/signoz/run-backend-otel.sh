#!/usr/bin/env bash
# Start the backend with the OpenTelemetry Java agent, exporting to SigNoz.
#
#   ./run-backend-otel.sh                     # temp instance on :38134
#   SERVER_PORT=38133 ./run-backend-otel.sh   # if nothing else uses 38133
#
# Runs the packaged boot jar (build it with `./gradlew bootJar` after code
# changes). The agent auto-instruments Spring MVC, JDBC, HTTP clients, scheduled
# jobs and JVM metrics — no application code changes.
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BACKEND_DIR="${SCRIPT_DIR}/../../backend"
AGENT="${OTEL_AGENT_JAR:-${SCRIPT_DIR}/opentelemetry-javaagent.jar}"
AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar"
JAR=$(ls "${BACKEND_DIR}"/build/libs/*.jar 2>/dev/null | grep -v -- '-plain' | head -1 || true)

if [ ! -f "$AGENT" ]; then
  echo "Downloading the OpenTelemetry Java agent..."
  curl -fL -o "$AGENT" "$AGENT_URL"
fi

if [ -z "$JAR" ]; then
  echo "No boot jar found; building it..."
  (cd "$BACKEND_DIR" && ./gradlew bootJar --console=plain)
  JAR=$(ls "${BACKEND_DIR}"/build/libs/*.jar | grep -v -- '-plain' | head -1)
fi

export SERVER_PORT="${SERVER_PORT:-38134}"
export DEV_TOOLS_ENABLED="${DEV_TOOLS_ENABLED:-true}"
export DB_POOL_SIZE="${DB_POOL_SIZE:-50}"
export SERVER_TOMCAT_THREADS_MAX="${SERVER_TOMCAT_THREADS_MAX:-500}"

export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-stitchstyle-backend}"
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"
export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"
export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-deployment.environment=local}"
export OTEL_TRACES_SAMPLER="${OTEL_TRACES_SAMPLER:-parentbased_always_on}"
export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-otlp}"
export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-otlp}"

# The backend targets Java 25; prefer the full JDK IntelliJ installed.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  if [ -x "${HOME}/.jdks/openjdk-25.0.2/bin/java" ]; then
    JAVA_HOME="${HOME}/.jdks/openjdk-25.0.2"
  fi
fi
JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"

echo "Starting ${JAR} on :${SERVER_PORT} with OTel -> ${OTEL_EXPORTER_OTLP_ENDPOINT}"
# Run from backend/ so spring.config.import finds ../.env.
cd "$BACKEND_DIR"
exec "$JAVA_BIN" -javaagent:"$AGENT" -jar "$JAR"
