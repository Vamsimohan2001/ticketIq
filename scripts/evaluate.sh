#!/usr/bin/env bash
# Scores the AI on 40 labelled tickets and writes docs/evaluation-report.md.
# Use the provider from .env, e.g. AI_PROVIDER=gemini GEMINI_API_KEY=... bash scripts/evaluate.sh
set -e
cd "$(dirname "$0")/.."
[ -f .env ] && { set -a; . ./.env; set +a; }
mvn -q -B -pl ticketiq-core -DskipTests package
java -cp ticketiq-core/target/classes com.ticketiq.core.eval.EvaluationRunner docs/evaluation-report.md
