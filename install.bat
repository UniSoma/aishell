@echo off
setlocal enabledelayedexpansion

REM install.bat - Installer for aishell (Windows CMD)
REM Downloads the aishell archive from GitHub Releases, verifies it against
REM the release's SHA256SUMS and unpacks the one aishell.exe inside. Nothing
REM else is installed.
REM
REM Usage: curl -fsSL https://raw.githubusercontent.com/UniSoma/aishell/main/install.bat -o install.bat && install.bat
REM
REM Environment variables:
REM   VERSION             - Version to install (default: latest)
REM   INSTALL_DIR         - Installation directory (default: %LOCALAPPDATA%\Programs\aishell)
REM   AISHELL_RELEASE_URL - Release base URL (default: the GitHub releases page)

REM --- Configuration ---
if defined AISHELL_RELEASE_URL (set "RELEASES_URL=%AISHELL_RELEASE_URL%") else (set "RELEASES_URL=https://github.com/UniSoma/aishell/releases")
if defined VERSION (set "VER=%VERSION%") else (set "VER=latest")
if defined INSTALL_DIR (set "INST_DIR=%INSTALL_DIR%") else (set "INST_DIR=%LOCALAPPDATA%\Programs\aishell")
set "ASSET=aishell-windows-amd64.zip"

REM --- Check for curl and tar ---
REM Both ship with Windows 10 version 1803 and later; tar there reads zip files.
where curl >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: curl is required but not found.
    echo curl is included in Windows 10 version 1803 and later.
    exit /b 1
)
where tar >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: tar is required to unpack the release archive but was not found.
    echo tar is included in Windows 10 version 1803 and later.
    exit /b 1
)

REM --- Create Install Directory ---
echo ==^> Creating installation directory...
if not exist "%INST_DIR%" mkdir "%INST_DIR%"
if not exist "%INST_DIR%" (
    echo Error: Failed to create directory %INST_DIR%
    exit /b 1
)

REM --- Determine Download URLs ---
if "%VER%"=="latest" (
    set "ASSET_BASE=%RELEASES_URL%/latest/download"
) else (
    set "ASSET_BASE=%RELEASES_URL%/download/v%VER%"
)
set "DOWNLOAD_URL=!ASSET_BASE!/%ASSET%"
set "CHECKSUM_URL=!ASSET_BASE!/SHA256SUMS"

REM --- Download (to a temp dir, so a bad download never touches the install) ---
set "TMP_DIR=%TEMP%\aishell-install-%RANDOM%%RANDOM%"
mkdir "%TMP_DIR%"
if not exist "%TMP_DIR%" (
    echo Error: Failed to create temporary directory %TMP_DIR%
    exit /b 1
)

echo ==^> Downloading %ASSET%...
curl -fsSL --retry 3 "!DOWNLOAD_URL!" -o "%TMP_DIR%\%ASSET%"
if !errorlevel! neq 0 (
    echo Error: Failed to download %ASSET% from !DOWNLOAD_URL!
    goto :fail
)

curl -fsSL --retry 3 "!CHECKSUM_URL!" -o "%TMP_DIR%\SHA256SUMS"
if !errorlevel! neq 0 (
    echo Error: Failed to download checksum from !CHECKSUM_URL!
    goto :fail
)

REM --- Verify Checksum ---
echo ==^> Verifying checksum...

REM Match the filename token exactly: "aishell" is a prefix of "%ASSET%".
set "EXPECTED_HASH="
for /f "usebackq tokens=1,2" %%a in ("%TMP_DIR%\SHA256SUMS") do (
    set "TOK=%%b"
    if "!TOK:~0,1!"=="*" set "TOK=!TOK:~1!"
    if /i "!TOK!"=="%ASSET%" if not defined EXPECTED_HASH set "EXPECTED_HASH=%%a"
)

REM Compute actual hash using certutil
REM certutil outputs: line1=algorithm header, line2=hash, line3=CertUtil success
set "ACTUAL_HASH="
for /f "skip=1 tokens=*" %%a in ('certutil -hashfile "%TMP_DIR%\%ASSET%" SHA256') do (
    if not defined ACTUAL_HASH set "ACTUAL_HASH=%%a"
)
REM Remove spaces from certutil output
set "ACTUAL_HASH=!ACTUAL_HASH: =!"

if not defined EXPECTED_HASH (
    echo Error: SHA256SUMS from !CHECKSUM_URL! lists no entry for %ASSET%
    goto :fail
)
if not defined ACTUAL_HASH (
    echo Error: Could not compute file checksum.
    goto :fail
)

REM certutil emits uppercase; /i makes the comparison case-insensitive.
if /i "!ACTUAL_HASH!" neq "!EXPECTED_HASH!" (
    echo Error: Checksum verification failed.
    echo   Expected: !EXPECTED_HASH!
    echo   Got:      !ACTUAL_HASH!
    goto :fail
)

echo ==^> Checksum verified.

REM --- Unpack ---
REM The archive holds one file, aishell.exe; it lands beside the download.
tar -xf "%TMP_DIR%\%ASSET%" -C "%TMP_DIR%" aishell.exe
if !errorlevel! neq 0 (
    echo Error: Failed to unpack %ASSET%
    goto :fail
)
if not exist "%TMP_DIR%\aishell.exe" (
    echo Error: %ASSET% does not contain aishell.exe
    goto :fail
)

REM --- Install ---
echo ==^> Installing...
move /y "%TMP_DIR%\aishell.exe" "%INST_DIR%\aishell.exe" >nul
if !errorlevel! neq 0 (
    echo Error: Failed to install to %INST_DIR%\aishell.exe
    goto :fail
)

REM Pre-4.1.0 installs put a bb-dependent script and its CMD shim here; both are
REM dead now that aishell.exe is on PATH. bb.exe is left alone - the old
REM installer may have put it there, and it is a tool in its own right.
if exist "%INST_DIR%\aishell" del "%INST_DIR%\aishell"
if exist "%INST_DIR%\aishell.bat" del "%INST_DIR%\aishell.bat"

rmdir /s /q "%TMP_DIR%" 2>nul

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
    echo ==^> Adding %INST_DIR% to user PATH...
    if defined CURRENT_PATH (
        setx Path "!CURRENT_PATH!;%INST_DIR%" >nul 2>&1
    ) else (
        setx Path "%INST_DIR%" >nul 2>&1
    )
    if !errorlevel! equ 0 (
        set "PATH_UPDATED=1"
        echo ==^> PATH updated.
    ) else (
        echo Warning: Failed to update PATH. You may need to add %INST_DIR% to PATH manually.
    )
) else (
    echo ==^> %INST_DIR% already in PATH.
)

REM --- Success Message ---
echo.
echo ==^> Done! Installed aishell to %INST_DIR%\aishell.exe
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
exit /b 0

:fail
if exist "%TMP_DIR%" rmdir /s /q "%TMP_DIR%" 2>nul
endlocal
exit /b 1
