# Scores the AI on 40 labelled tickets and writes docs/evaluation-report.md.
Set-Location (Join-Path $PSScriptRoot "..")
if (Test-Path .env) { Get-Content .env | ForEach-Object { if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2]) } } }
mvn -q -B -pl ticketiq-core -DskipTests package
java -cp ticketiq-core/target/classes com.ticketiq.core.eval.EvaluationRunner docs/evaluation-report.md
