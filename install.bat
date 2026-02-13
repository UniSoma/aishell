@echo off
setlocal enabledelayedexpansion

REM install.bat - Installer for aishell (Windows CMD)
REM Downloads and installs Babashka + aishell with checksum verification
REM
REM Usage: curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.bat -o install.bat && install.bat
REM
REM Environment variables:
REM   VERSION     - Version to install (default: latest)
REM   INSTALL_DIR - Installation directory (default: %LOCALAPPDATA%\Programs\aishell)

REM --- Configuration ---
set "REPO_URL=https://github.com/UniSoma/aishell"
if defined VERSION (set "VER=%VERSION%") else (set "VER=latest")
if defined INSTALL_DIR (set "INST_DIR=%INSTALL_DIR%") else (set "INST_DIR=%LOCALAPPDATA%\Programs\aishell")

REM --- Check for curl ---
where curl >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: curl is required but not found.
    echo curl is included in Windows 10 version 1803 and later.
    exit /b 1
)

REM --- Create Install Directory ---
echo ==> Creating installation directory...
if not exist "%INST_DIR%" mkdir "%INST_DIR%"
if %errorlevel% neq 0 (
    echo Error: Failed to create directory %INST_DIR%
    exit /b 1
)

REM --- Check for Babashka ---
where bb >nul 2>&1
if %errorlevel% equ 0 (
    echo ==> Babashka found.
    goto :bb_done
)

echo ==> Babashka not found. Installing...

REM Try Scoop first
where scoop >nul 2>&1
if %errorlevel% equ 0 (
    echo ==> Installing Babashka via Scoop...
    scoop install babashka
    if !errorlevel! equ 0 (
        echo ==> Babashka installed via Scoop.
        goto :bb_done
    )
    echo Warning: Scoop install failed. Trying direct download...
)

REM Direct download from GitHub
echo ==> Installing Babashka directly from GitHub...

REM Get latest version by following redirect
set "BB_VER="
for /f "tokens=2 delims= " %%a in ('curl -sI "https://github.com/babashka/babashka/releases/latest" 2^>nul ^| findstr /i "^location:"') do (
    set "BB_LOCATION=%%a"
)
if not defined BB_LOCATION (
    echo Error: Failed to determine latest Babashka version.
    exit /b 1
)
REM Strip trailing CR/LF from the location header
set "BB_LOCATION=!BB_LOCATION: =!"
for /f "delims=" %%a in ("!BB_LOCATION!") do set "BB_LOCATION=%%a"
REM Extract version from URL (last segment after /v)
for %%a in ("!BB_LOCATION:/=" "!") do set "BB_VER=%%~a"
REM Remove leading 'v' if present
if "!BB_VER:~0,1!"=="v" set "BB_VER=!BB_VER:~1!"
REM Remove trailing carriage return
set "BB_VER=!BB_VER: =!"
for /f "delims=" %%a in ("!BB_VER!") do set "BB_VER=%%a"

if not defined BB_VER (
    echo Error: Failed to parse Babashka version from redirect.
    exit /b 1
)

set "BB_URL=https://github.com/babashka/babashka/releases/download/v!BB_VER!/babashka-!BB_VER!-windows-amd64.zip"
set "BB_ZIP=%TEMP%\babashka.zip"

echo ==> Downloading Babashka v!BB_VER!...
curl -fsSL "!BB_URL!" -o "!BB_ZIP!"
if !errorlevel! neq 0 (
    echo Error: Failed to download Babashka from !BB_URL!
    if exist "!BB_ZIP!" del "!BB_ZIP!"
    exit /b 1
)

echo ==> Extracting Babashka to %INST_DIR%...
REM Try tar first (built-in on Windows 10+)
tar -xf "!BB_ZIP!" -C "%INST_DIR%" >nul 2>&1
if !errorlevel! neq 0 (
    REM Fallback to PowerShell Expand-Archive
    powershell -NoProfile -Command "Expand-Archive -Path '!BB_ZIP!' -DestinationPath '%INST_DIR%' -Force" >nul 2>&1
    if !errorlevel! neq 0 (
        echo Error: Failed to extract Babashka. Neither tar nor PowerShell extraction worked.
        del "!BB_ZIP!"
        exit /b 1
    )
)

del "!BB_ZIP!"

if not exist "%INST_DIR%\bb.exe" (
    echo Error: Babashka installation failed - bb.exe not found.
    exit /b 1
)

echo ==> Babashka installed to %INST_DIR%\bb.exe

:bb_done

REM --- Determine Download URLs ---
if "%VER%"=="latest" (
    set "DOWNLOAD_URL=%REPO_URL%/releases/latest/download/aishell"
    set "BAT_URL=%REPO_URL%/releases/latest/download/aishell.bat"
) else (
    set "DOWNLOAD_URL=%REPO_URL%/releases/download/v%VER%/aishell"
    set "BAT_URL=%REPO_URL%/releases/download/v%VER%/aishell.bat"
)
set "CHECKSUM_URL=%DOWNLOAD_URL%.sha256"

REM --- Download aishell ---
echo ==> Downloading aishell...
curl -fsSL "%DOWNLOAD_URL%" -o "%INST_DIR%\aishell"
if %errorlevel% neq 0 (
    echo Error: Failed to download aishell from %DOWNLOAD_URL%
    if exist "%INST_DIR%\aishell" del "%INST_DIR%\aishell"
    exit /b 1
)

REM --- Download aishell.bat ---
echo ==> Downloading aishell.bat...
curl -fsSL "%BAT_URL%" -o "%INST_DIR%\aishell.bat"
if %errorlevel% neq 0 (
    echo Error: Failed to download aishell.bat from %BAT_URL%
    if exist "%INST_DIR%\aishell" del "%INST_DIR%\aishell"
    if exist "%INST_DIR%\aishell.bat" del "%INST_DIR%\aishell.bat"
    exit /b 1
)

REM --- Download Checksum ---
echo ==> Downloading checksum...
curl -fsSL "%CHECKSUM_URL%" -o "%TEMP%\aishell.sha256"
if %errorlevel% neq 0 (
    echo Error: Failed to download checksum from %CHECKSUM_URL%
    if exist "%INST_DIR%\aishell" del "%INST_DIR%\aishell"
    if exist "%INST_DIR%\aishell.bat" del "%INST_DIR%\aishell.bat"
    if exist "%TEMP%\aishell.sha256" del "%TEMP%\aishell.sha256"
    exit /b 1
)

REM --- Verify Checksum ---
echo ==> Verifying checksum...

REM Read expected hash (first token from checksum file)
set "EXPECTED_HASH="
for /f "tokens=1" %%a in (%TEMP%\aishell.sha256) do (
    if not defined EXPECTED_HASH set "EXPECTED_HASH=%%a"
)

REM Compute actual hash using certutil
set "ACTUAL_HASH="
REM certutil outputs: line1=algorithm header, line2=hash, line3=CertUtil success
set "HASH_LINE=0"
for /f "skip=1 tokens=*" %%a in ('certutil -hashfile "%INST_DIR%\aishell" SHA256') do (
    if not defined ACTUAL_HASH set "ACTUAL_HASH=%%a"
)
REM Remove spaces from certutil output
set "ACTUAL_HASH=!ACTUAL_HASH: =!"

if not defined EXPECTED_HASH (
    echo Error: Could not read expected checksum.
    goto :checksum_fail
)
if not defined ACTUAL_HASH (
    echo Error: Could not compute file checksum.
    goto :checksum_fail
)

if /i "!ACTUAL_HASH!" neq "!EXPECTED_HASH!" (
    echo Error: Checksum verification failed.
    echo   Expected: !EXPECTED_HASH!
    echo   Got:      !ACTUAL_HASH!
    goto :checksum_fail
)

echo ==> Checksum verified.
del "%TEMP%\aishell.sha256"
goto :checksum_ok

:checksum_fail
if exist "%INST_DIR%\aishell" del "%INST_DIR%\aishell"
if exist "%INST_DIR%\aishell.bat" del "%INST_DIR%\aishell.bat"
if exist "%TEMP%\aishell.sha256" del "%TEMP%\aishell.sha256"
exit /b 1

:checksum_ok

REM --- PATH Management ---
set "PATH_UPDATED=0"

REM Read current user PATH from registry
set "CURRENT_PATH="
for /f "tokens=2,*" %%a in ('reg query "HKCU\Environment" /v Path 2^>nul ^| findstr /i "path"') do (
    set "CURRENT_PATH=%%b"
)

REM Check if install dir is already in PATH
echo !CURRENT_PATH! | findstr /i /c:"%INST_DIR%" >nul 2>&1
if %errorlevel% neq 0 (
    echo ==> Adding %INST_DIR% to user PATH...
    if defined CURRENT_PATH (
        setx Path "!CURRENT_PATH!;%INST_DIR%" >nul 2>&1
    ) else (
        setx Path "%INST_DIR%" >nul 2>&1
    )
    if !errorlevel! equ 0 (
        set "PATH_UPDATED=1"
        echo ==> PATH updated.
    ) else (
        echo Warning: Failed to update PATH. You may need to add %INST_DIR% to PATH manually.
    )
) else (
    echo ==> %INST_DIR% already in PATH.
)

REM --- Success Message ---
echo.
echo ==> Done! Installed aishell to %INST_DIR%
echo.

if "%PATH_UPDATED%"=="1" (
    echo Warning: Restart your terminal for PATH changes to take effect.
    echo.
    echo Then run:
) else (
    echo Quick start:
)
echo   aishell setup --with-opencode   # Set up Docker image and select harnesses
echo   aishell opencode                # Run OpenCode
echo.

endlocal
