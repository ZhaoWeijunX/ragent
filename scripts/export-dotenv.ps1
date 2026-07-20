# Load repo-root .env into the current PowerShell session.
# Usage (from repo root):
#   . .\scripts\export-dotenv.ps1
#   mvn -pl bootstrap spring-boot:run

$ErrorActionPreference = "Stop"
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env not found at $envFile. Copy .env.example to .env and fill values."
}

Get-Content -Path $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        return
    }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) {
        return
    }
    $name = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    Set-Item -Path "Env:$name" -Value $value
    Write-Host "exported $name"
}

Write-Host "Loaded $envFile"
