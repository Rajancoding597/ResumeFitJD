@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
pushd "%PROJECT_ROOT%" >nul

set "POWERSHELL_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

"%POWERSHELL_EXE%" -ExecutionPolicy Bypass -File ".\scripts\Ensure-ProjectTools.ps1"
if errorlevel 1 goto :done

"%POWERSHELL_EXE%" -ExecutionPolicy Bypass -File ".\scripts\Run-Local.ps1"

:done
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul
exit /b %EXIT_CODE%
