@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
:: Il file si trova in plugins\pampa-store-publisher\, quindi ..\.. risale alla root del progetto
set "REPO_ROOT=%SCRIPT_DIR%..\.."

if "%PAMPA_STORE_REPO_ROOT%"=="" (
  set "PAMPA_STORE_REPO_ROOT=%REPO_ROOT%"
)

:: Entra nella cartella del progetto
pushd "%REPO_ROOT%" >nul

:: Prova 'py' prima (più affidabile su Windows), poi 'python'
where py >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  py -3 "%SCRIPT_DIR%mcp\server.py"
) else (
  where python >nul 2>nul
  if %ERRORLEVEL% EQU 0 (
    python "%SCRIPT_DIR%mcp\server.py"
  ) else (
    echo ERROR: Python not found. Please install Python. >&2
    exit /b 1
  )
)

set "EXITCODE=%ERRORLEVEL%"
popd >nul
exit /b %EXITCODE%
