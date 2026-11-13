#!/usr/bin/env bash
# Checks prerequisites and creates .env. Safe to run any time.
set -e
cd "$(dirname "$0")/.."

echo "== TicketIQ setup =="
if ! command -v java >/dev/null 2>&1; then
  echo "Java is missing. Install JDK 21:"
  echo "  macOS:   brew install openjdk@21"
  echo "  Ubuntu:  sudo apt install openjdk-21-jdk"
  echo "  Windows: winget install EclipseAdoptium.Temurin.21.JDK"
  exit 1
fi
JAVA_MAJOR=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
if [ "$JAVA_MAJOR" -lt 21 ]; then echo "Java $JAVA_MAJOR found, but TicketIQ needs 21 or newer."; exit 1; fi
echo "Java $JAVA_MAJOR ... ok"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is missing. Install it:"
  echo "  macOS:   brew install maven"
  echo "  Ubuntu:  sudo apt install maven"
  echo "  Windows: winget install Apache.Maven"
  exit 1
fi
echo "Maven $(mvn -v | head -1 | awk '{print $3}') ... ok"

if [ ! -f .env ]; then cp .env.example .env && echo "Created .env from .env.example"; else echo ".env already exists"; fi
echo "Setup complete. Next: bash scripts/run.sh"
