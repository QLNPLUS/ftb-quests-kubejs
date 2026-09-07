$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$targets = @(
    @{ Directory = "forge-1.19.2"; Java = "C:\Program Files\Java\jdk-17" },
    @{ Directory = "forge-1.20.1"; Java = "C:\Program Files\Java\jdk-17" },
    @{ Directory = "neoforge-1.21.1"; Java = "C:\Program Files\Java\jdk-21" },
    @{ Directory = "neoforge-1.26.1.2"; Java = "C:\Program Files\Java\jdk-25.0.4.1" }
)

foreach ($target in $targets) {
    $dir = Join-Path $root $target.Directory
    $javaHome = $target.Java
    if (-not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
        throw "Java toolchain not found: $javaHome"
    }

    Write-Host "=== Building $($target.Directory) with $javaHome ===" -ForegroundColor Cyan
    $env:JAVA_HOME = $javaHome
    Push-Location $dir
    try {
        & .\gradlew.bat clean build --no-daemon --stacktrace
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for $($target.Directory) with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

& (Join-Path $PSScriptRoot "validate-artifacts.ps1")
