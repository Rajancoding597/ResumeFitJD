$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkHome = Join-Path $projectRoot "tools\jdk-21"
$mavenHome = Join-Path $projectRoot "tools\maven"
$repoLocal = Join-Path $projectRoot ".m2\repository"
$envFile = Join-Path $projectRoot ".env"

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $parts = $line.Split("=", 2)
        if ($parts.Count -ne 2) {
            return
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($value.StartsWith('"') -and $value.EndsWith('"')) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ($value.StartsWith("'") -and $value.EndsWith("'")) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        Set-Item -Path "Env:$name" -Value $value
    }
}

if (-not (Test-Path (Join-Path $jdkHome "bin\java.exe"))) {
    throw "Project-local JDK not found. Run .\scripts\Ensure-ProjectTools.ps1 first."
}

if (-not (Test-Path (Join-Path $mavenHome "bin\mvn.cmd"))) {
    throw "Project-local Maven not found. Run .\scripts\Ensure-ProjectTools.ps1 first."
}

Import-DotEnv -Path $envFile

if (-not $env:GEMINI_API_KEY) {
    Write-Host "GEMINI_API_KEY is not set. Starting in BYOK mode; enter a Gemini API key in the UI."
}

New-Item -ItemType Directory -Force -Path $repoLocal | Out-Null

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$mavenHome\bin;$env:Path"

if (Test-Path (Join-Path $projectRoot "target")) {
    Remove-Item -Recurse -Force (Join-Path $projectRoot "target")
}

& (Join-Path $mavenHome "bin\mvn.cmd") "-Dmaven.repo.local=$repoLocal" spring-boot:run
