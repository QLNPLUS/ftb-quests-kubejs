$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$targets = @(
    @{ Directory = "forge-1.19.2"; Java = "C:\Program Files\Java\jdk-17" },
    @{ Directory = "forge-1.20.1"; Java = "C:\Program Files\Java\jdk-17" },
    @{ Directory = "neoforge-1.21.1"; Java = "C:\Program Files\Java\jdk-21" },
    @{ Directory = "neoforge-1.26.1.2"; Java = "C:\Program Files\Java\jdk-25.0.4.1" }
)

$results = @()
foreach ($target in $targets) {
    $dir = Join-Path $root $target.Directory
    $log = Join-Path $dir "runs\client\logs\latest.log"
    $logDir = Split-Path -Parent $log
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    if (Test-Path $log) {
        $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
        Move-Item -LiteralPath $log -Destination (Join-Path $logDir "latest-$stamp.log")
    }

    Write-Host "=== Smoke testing $($target.Directory) ===" -ForegroundColor Cyan
    $env:JAVA_HOME = $target.Java
    Push-Location $dir
    try {
        $process = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "runClient --no-daemon" -WorkingDirectory $dir -PassThru -WindowStyle Hidden
        $deadline = (Get-Date).AddMinutes(8)
        $started = $false
        $failure = $null

        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 5
            if (Test-Path $log) {
                $text = Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue
                if ($text -match "Starting Minecraft|Loading Minecraft|Preparing spawn area|FML|NeoForge|Forge mod loading") {
                    $started = $true
                    break
                }
                if ($text -match "Exception|Error loading|Failed to load|Mixin.*failed|Could not load") {
                    $failure = ($text -split "`r?`n" | Select-String -Pattern "Exception|Error loading|Failed to load|Mixin.*failed|Could not load" | Select-Object -First 5).ToString()
                    break
                }
            }
            if ($process.HasExited) {
                break
            }
        }

        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
        }

        if ($failure) {
            throw $failure
        }
        if (-not $started) {
            throw "No startup marker was found before timeout or process exit. See $log"
        }

        $results += [pscustomobject]@{ Project = $target.Directory; Result = "PASS"; Log = $log }
        Write-Host "PASS $($target.Directory)" -ForegroundColor Green
    }
    catch {
        $results += [pscustomobject]@{ Project = $target.Directory; Result = "FAIL"; Log = $log }
        Write-Host "FAIL $($target.Directory): $($_.Exception.Message)" -ForegroundColor Red
    }
    finally {
        Pop-Location
    }
}

$results | Format-Table -AutoSize
if ($results.Result -contains "FAIL") {
    exit 1
}
