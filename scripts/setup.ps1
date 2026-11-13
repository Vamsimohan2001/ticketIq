# Checks prerequisites and creates .env (Windows PowerShell).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
Write-Host "== TicketIQ setup =="
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  Write-Host "Java is missing. Install it with: winget install EclipseAdoptium.Temurin.21.JDK"; exit 1 }
$v = (& java -version 2>&1 | Select-Object -First 1) -replace '.*"(\d+).*', '$1'
if ([int]$v -lt 21) { Write-Host "Java $v found, but TicketIQ needs 21 or newer."; exit 1 }
Write-Host "Java $v ... ok"
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
  Write-Host "Maven is missing. Install it with: winget install Apache.Maven"; exit 1 }
Write-Host "Maven ... ok"
if (-not (Test-Path .env)) { Copy-Item .env.example .env; Write-Host "Created .env from .env.example" } else { Write-Host ".env already exists" }
Write-Host "Setup complete. Next: powershell -File scripts/run.ps1"
