#!/bin/bash
# Measures API startup under Render's free-tier limits (0.1 CPU, 512 MB), against the compose Postgres.
# Prints the time from `docker run` to the first healthy /actuator/health, Spring's own "Started in",
# memory after startup, and the status of a first real API call.
#
# Usage: scripts/measure-startup.sh <name> <host-port> <image> [extra docker run args...]
# Example: docker compose up -d postgres && scripts/measure-startup.sh shipped 18090 pariscompass-api
set -u
NAME=$1; PORT=$2; IMG=$3; shift 3
docker rm -f "$NAME" >/dev/null 2>&1
start=$(python3 -c 'import time; print(time.time())')
docker run -d --name "$NAME" --network pariscompass_default --cpus=0.1 --memory=512m -p "$PORT:8081" \
  -e DATABASE_URL=jdbc:postgresql://postgres:5432/pariscompass -e DATABASE_USERNAME=pariscompass \
  -e DATABASE_PASSWORD=pariscompass -e GEMINI_API_KEY=dummy-not-used -e CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  "$@" "$IMG" >/dev/null
until curl -sf "localhost:$PORT/actuator/health" >/dev/null 2>&1; do
  sleep 0.5
  docker ps -q -f "name=^$NAME$" | grep -q . || { echo "$NAME: container exited"; docker logs "$NAME" 2>&1 | tail -3; exit 1; }
done
end=$(python3 -c 'import time; print(time.time())')
started=$(docker logs "$NAME" 2>&1 | grep -o "Started ParisCompassApplication in [0-9.]* seconds" | grep -o "[0-9.]*")
mem=$(docker stats --no-stream --format '{{.MemUsage}}' "$NAME" | cut -d/ -f1)
code=$(curl -s -o /dev/null -w "%{http_code}" "localhost:$PORT/api/countries/USA")
printf "%-22s healthy after %6.1f s | Spring %7s s | memory %s | first API call HTTP %s\n" \
  "$NAME" "$(python3 -c "print($end-$start)")" "$started" "$mem" "$code"
docker rm -f "$NAME" >/dev/null 2>&1
