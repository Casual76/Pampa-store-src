@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "REPO_ROOT=%SCRIPT_DIR%..\.."

if "%PAMPA_STORE_REPO_ROOT%"=="" (
  set "PAMPA_STORE_REPO_ROOT=%REPO_ROOT%"
)

pushd "%REPO_ROOT%" >nul

:: Forza Python in modalità unbuffered (-u) e binaria
where py >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  py -3 -u "%SCRIPT_DIR%mcp\server.py"
) else (
  where python >nul 2>nul
  if %ERRORLEVEL% EQU 0 (
    python -u "%SCRIPT_DIR%mcp\server.py"
  ) else (
    echo ERROR: Python not found. >&2
    exit /b 1
  )
)

set "EXITCODE=%ERRORLEVEL%"
popd >nul
exit /b %EXITCODE%
