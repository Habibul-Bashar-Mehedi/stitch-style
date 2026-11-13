#!/usr/bin/env bash
# Run one Gatling simulation.
#
#   ./run.sh com.stitchstyle.gatling.simulations.CatalogueSimulation
#   VUS=500 RAMP=20 HOLD=20 ./run.sh com.stitchstyle.gatling.simulations.StressSimulation
#
# Picks a full JDK (one with javac). The default java on some machines is a
# JRE-only install, which Gatling cannot compile with.
set -euo pipefail

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/javac" ]; then
  for candidate in \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk-amd64 \
    /usr/lib/jvm/java-11-openjdk-amd64; do
    if [ -x "${candidate}/bin/javac" ]; then
      export JAVA_HOME="${candidate}"
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/javac" ]; then
  echo "No full JDK (with javac) found. Set JAVA_HOME to one and retry." >&2
  exit 1
fi

SIM="${1:-}"
if [ -z "$SIM" ]; then
  echo "Usage: $0 <SimulationClass> [mvn args...]" >&2
  exit 1
fi
shift

cd "$(dirname "$0")"
exec mvn -q gatling:test -Dgatling.simulationClass="$SIM" "$@"
