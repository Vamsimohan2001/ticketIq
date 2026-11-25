#!/bin/sh
set -eu

case "${TICKETIQ_DB_URL:-}" in
  postgres://*|postgresql://*)
    TICKETIQ_DB_URL="jdbc:${TICKETIQ_DB_URL}"
    export TICKETIQ_DB_URL
    ;;
esac

if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
  export JWT_SECRET
fi

exec java -jar /app/ticketiq-app.jar
