#!/usr/bin/env bash
# Runs every test (core unit tests and the Spring integration tests). No API key needed.
set -e
cd "$(dirname "$0")/.."
mvn -B verify
