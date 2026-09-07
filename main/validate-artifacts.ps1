$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$targets = @(
    @{ Directory = "forge-1.19.2"; PackFormat = 9 },
    @{ Directory = "forge-1.20.1"; PackFormat = 15 },
    @{ Directory = "neoforge-1.21.1"; PackFormat = 34 },
    @{ Directory = "neoforge-1.26.1.2"; PackFormat = 84 }
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

foreach ($target in $targets) {
    $jarPath = Join-Path $root "$($target.Directory)\build\libs\ftb-quests-kubejs-0.1.0.jar"
    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "Release JAR not found: $jarPath"
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $entry = $archive.GetEntry("pack.mcmeta")
        if ($null -eq $entry) {
            throw "$($target.Directory): pack.mcmeta is missing from the JAR root"
        }

        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            $metadata = $reader.ReadToEnd() | ConvertFrom-Json
        }
        finally {
            $reader.Dispose()
        }

        if ([string]::IsNullOrWhiteSpace([string]$metadata.pack.description)) {
            throw "$($target.Directory): pack.description is missing"
        }
        if ([int]$metadata.pack.pack_format -ne $target.PackFormat) {
            throw "$($target.Directory): expected pack_format $($target.PackFormat), got $($metadata.pack.pack_format)"
        }
    }
    finally {
        $archive.Dispose()
    }

    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash
    $size = (Get-Item -LiteralPath $jarPath).Length
    Write-Host "PASS $($target.Directory): pack.mcmeta, ${size} bytes, SHA256 $hash" -ForegroundColor Green
}
