@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "REPO_ROOT=%SCRIPT_DIR%.."

if "%PAMPA_STORE_REPO_ROOT%"=="" (
  set "PAMPA_STORE_REPO_ROOT=%REPO_ROOT%"
)

pushd "%REPO_ROOT%" >nul

where py >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  py -3 "%SCRIPT_DIR%mcp\server.py"
) else (
  python "%SCRIPT_DIR%mcp\server.py"
)

set "EXITCODE=%ERRORLEVEL%"
popd >nul
exit /b %EXITCODE%
