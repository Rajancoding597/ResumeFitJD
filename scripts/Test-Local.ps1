$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkHome = Join-Path $projectRoot "tools\jdk-21"
$mavenHome = Join-Path $projectRoot "tools\maven"
$repoLocal = Join-Path $projectRoot ".m2\repository"

if (-not (Test-Path (Join-Path $jdkHome "bin\java.exe"))) {
    throw "Project-local JDK not found. Run .\scripts\Ensure-ProjectTools.ps1 first."
}

if (-not (Test-Path (Join-Path $mavenHome "bin\mvn.cmd"))) {
    throw "Project-local Maven not found. Run .\scripts\Ensure-ProjectTools.ps1 first."
}

New-Item -ItemType Directory -Force -Path $repoLocal | Out-Null

$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$mavenHome\bin;$env:Path"

if (Test-Path (Join-Path $projectRoot "target")) {
    Remove-Item -Recurse -Force (Join-Path $projectRoot "target")
}

& (Join-Path $mavenHome "bin\mvn.cmd") "-Dmaven.repo.local=$repoLocal" test
