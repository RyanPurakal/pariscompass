#!/bin/bash
# Runs the backend with the dev profile. Loads .env if present.
# GEMINI_API_KEY is optional in dev: without it, the projection endpoint returns 503.

if [ -f .env ]; then
    set -a; source .env; set +a
fi

if [ -z "$GEMINI_API_KEY" ]; then
    echo "Note: GEMINI_API_KEY is not set. Projections will return 503; everything else works."
fi

echo "Starting Paris Compass backend on http://localhost:${PORT:-8081} (profile: ${SPRING_PROFILES_ACTIVE:-dev})"
./mvnw spring-boot:run
