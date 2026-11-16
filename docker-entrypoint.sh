#!/bin/sh
set -eu

if [ -z "${JWT_SECRET:-}" ]; then
  JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
  export JWT_SECRET
fi

exec java -jar /app/ticketiq-app.jar
