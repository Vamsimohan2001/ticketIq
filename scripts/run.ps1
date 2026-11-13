# Builds everything and starts TicketIQ, then opens it in your browser (Windows PowerShell).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2]) } }
$port = if ($env:PORT) { $env:PORT } else { "8080" }

Write-Host "Building (first run downloads dependencies, this can take a few minutes)..."
mvn -q -B -DskipTests install
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Start-Job -ArgumentList $port -ScriptBlock {
  param($p)
  $url = "http://localhost:$p"
  $programFilesX86 = [Environment]::GetEnvironmentVariable("ProgramFiles(x86)")
  $browsers = @(
    @{ Path = "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"; Flag = "--new-window" },
    @{ Path = "$programFilesX86\Microsoft\Edge\Application\msedge.exe"; Flag = "--new-window" },
    @{ Path = "$env:ProgramFiles\Google\Chrome\Application\chrome.exe"; Flag = "--new-window" },
    @{ Path = "$programFilesX86\Google\Chrome\Application\chrome.exe"; Flag = "--new-window" },
    @{ Path = "$env:ProgramFiles\BraveSoftware\Brave-Browser\Application\brave.exe"; Flag = "--new-window" },
    @{ Path = "$programFilesX86\BraveSoftware\Brave-Browser\Application\brave.exe"; Flag = "--new-window" },
    @{ Path = "$env:LOCALAPPDATA\Microsoft\Edge\Application\msedge.exe"; Flag = "--new-window" },
    @{ Path = "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"; Flag = "--new-window" },
    @{ Path = "$env:LOCALAPPDATA\BraveSoftware\Brave-Browser\Application\brave.exe"; Flag = "--new-window" },
    @{ Path = "$env:ProgramFiles\Mozilla Firefox\firefox.exe"; Flag = "-new-window" },
    @{ Path = "$programFilesX86\Mozilla Firefox\firefox.exe"; Flag = "-new-window" }
  )
  for ($i = 0; $i -lt 90; $i++) {
    $healthy = $false
    try {
      Invoke-WebRequest "$url/actuator/health" -UseBasicParsing | Out-Null
      $healthy = $true
    }
    catch { Start-Sleep -Seconds 2 }
    if ($healthy) {
      $opened = $false
      foreach ($browser in $browsers) {
        if ($browser.Path -and (Test-Path $browser.Path)) {
          Start-Process -FilePath $browser.Path -ArgumentList @($browser.Flag, $url)
          $opened = $true
          break
        }
      }
      if (-not $opened) { Start-Process $url }
      break
    }
  }
} | Out-Null

mvn -q -B -pl ticketiq-app spring-boot:run
