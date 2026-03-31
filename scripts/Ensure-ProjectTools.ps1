$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $projectRoot "tools"
$jdkTarget = Join-Path $toolsDir "jdk-21"
$mavenTarget = Join-Path $toolsDir "maven"

New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

function Find-Jdk21Source {
    $candidates = @(
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }

        $resolved = Get-ChildItem -Path $candidate -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved) {
            return $resolved.FullName
        }
    }

    return $null
}

function Find-MavenSource {
    $candidates = @(
        "C:\Program Files\JetBrains\IntelliJ IDEA 2024.2.3\plugins\maven\lib\maven3",
        "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.2.3\plugins\maven\lib\maven3",
        "C:\Program Files\Apache\Maven",
        "C:\apache-maven*"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }

        $resolved = Get-ChildItem -Path $candidate -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($resolved) {
            return $resolved.FullName
        }
    }

    return $null
}

function Copy-ToolTree {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    Write-Host "Copying $Source -> $Destination"
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    robocopy $Source $Destination /E /NFL /NDL /NJH /NJS /NP | Out-Null

    if ($LASTEXITCODE -gt 7) {
        throw "Failed to copy $Source to $Destination (robocopy exit code $LASTEXITCODE)."
    }
}

if (-not (Test-Path (Join-Path $jdkTarget "bin\java.exe"))) {
    if (Test-Path $jdkTarget) {
        Remove-Item -Recurse -Force $jdkTarget
    }
    $jdkSource = Find-Jdk21Source
    if (-not $jdkSource) {
        throw "Could not find a local JDK 21 installation to copy into tools\jdk-21."
    }
    Copy-ToolTree -Source $jdkSource -Destination $jdkTarget
} else {
    Write-Host "Project-local JDK already present at $jdkTarget"
}

if (-not (Test-Path (Join-Path $mavenTarget "bin\mvn.cmd"))) {
    if (Test-Path $mavenTarget) {
        Remove-Item -Recurse -Force $mavenTarget
    }
    $mavenSource = Find-MavenSource
    if (-not $mavenSource) {
        throw "Could not find a local Maven installation to copy into tools\maven."
    }
    Copy-ToolTree -Source $mavenSource -Destination $mavenTarget
} else {
    Write-Host "Project-local Maven already present at $mavenTarget"
}

Write-Host "Project-local tools are ready."
Write-Host "JDK:   $jdkTarget"
Write-Host "Maven: $mavenTarget"
