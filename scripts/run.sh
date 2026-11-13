#!/usr/bin/env bash
# Builds everything and starts TicketIQ, then opens it in your browser.
set -e
cd "$(dirname "$0")/.."
[ -f .env ] || cp .env.example .env
set -a; . ./.env; set +a
PORT="${PORT:-8080}"

open_browser_window() {
  url="http://localhost:$PORT"
  for browser in chromium chromium-browser google-chrome google-chrome-stable chrome; do
    if command -v "$browser" >/dev/null 2>&1; then
      "$browser" --new-window "$url" >/dev/null 2>&1 &
      return 0
    fi
  done
  if command -v firefox >/dev/null 2>&1; then
    firefox --new-window "$url" >/dev/null 2>&1 &
    return 0
  fi
  if command -v open >/dev/null 2>&1; then
    if open -na "Google Chrome" --args --new-window "$url" >/dev/null 2>&1; then return 0; fi
    if open -na "Firefox" --args -new-window "$url" >/dev/null 2>&1; then return 0; fi
    open "$url" >/dev/null 2>&1 || true
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$url" >/dev/null 2>&1 || true
  fi
}

echo "Building (first run downloads dependencies, this can take a few minutes)..."
mvn -q -B -DskipTests install

(
  for _ in $(seq 1 90); do
    if curl -fs "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; then
      echo "TicketIQ is up: http://localhost:$PORT"
      open_browser_window
      exit 0
    fi
    sleep 2
  done
) &

mvn -q -B -pl ticketiq-app spring-boot:run
