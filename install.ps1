# install.ps1 - Installer for aishell (Windows)
# Downloads the aishell binary from GitHub Releases and verifies it against the
# release's SHA256SUMS. Nothing else is installed.
#
# Usage: irm https://raw.githubusercontent.com/UniSoma/aishell/main/install.ps1 | iex
#
# Environment variables:
#   $env:VERSION             - Version to install (default: latest)
#   $env:INSTALL_DIR         - Installation directory (default: $env:LOCALAPPDATA\Programs\aishell)
#   $env:AISHELL_RELEASE_URL - Release base URL (default: the GitHub releases page)

$ErrorActionPreference = "Stop"

# --- Configuration ---
$releasesUrl = if ($env:AISHELL_RELEASE_URL) { $env:AISHELL_RELEASE_URL } else { "https://github.com/UniSoma/aishell/releases" }
$version = if ($env:VERSION) { $env:VERSION } else { "latest" }
$installDir = if ($env:INSTALL_DIR) { $env:INSTALL_DIR } else { "$env:LOCALAPPDATA\Programs\aishell" }
$asset = "aishell-windows-amd64.exe"
$destPath = Join-Path $installDir "aishell.exe"

# --- Output Functions ---
function Write-Info {
    param([string]$Message)
    Write-Host "==> " -ForegroundColor Blue -NoNewline
    Write-Host $Message -ForegroundColor White
}

function Write-Success {
    param([string]$Message)
    Write-Host "==> " -ForegroundColor Green -NoNewline
    Write-Host $Message
}

function Write-Error {
    param([string]$Message)
    Write-Host "Error: " -ForegroundColor Red -NoNewline
    Write-Host $Message
}

function Write-Warn {
    param([string]$Message)
    Write-Host "Warning: " -ForegroundColor Yellow -NoNewline
    Write-Host $Message
}

# --- Create Install Directory ---
Write-Info "Creating installation directory..."
New-Item -ItemType Directory -Force -Path $installDir | Out-Null

# --- Determine Download URLs ---
$assetBase = if ($version -eq "latest") {
    "$releasesUrl/latest/download"
} else {
    "$releasesUrl/download/v${version}"
}
$downloadUrl = "$assetBase/$asset"
$checksumUrl = "$assetBase/SHA256SUMS"

# --- Download (to a temp dir, so a bad download never touches the install) ---
$tmpDir = Join-Path ([IO.Path]::GetTempPath()) ("aishell-install-" + [IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

try {
    $tmpAsset = Join-Path $tmpDir $asset
    $tmpSums = Join-Path $tmpDir "SHA256SUMS"

    # The asset is ~70 MB; Windows PowerShell 5.1 renders download progress one
    # write at a time, which dominates the transfer. Restored in the finally
    # below, because "irm | iex" runs this in the caller's own session.
    $previousProgressPreference = $ProgressPreference
    $ProgressPreference = "SilentlyContinue"

    Write-Info "Downloading ${asset} (about 70 MB); this takes a while..."
    try {
        Invoke-WebRequest -Uri $downloadUrl -OutFile $tmpAsset -UseBasicParsing
    } catch {
        Write-Error "Failed to download ${asset} from ${downloadUrl}: $_"
        exit 1
    }

    try {
        Invoke-WebRequest -Uri $checksumUrl -OutFile $tmpSums -UseBasicParsing
    } catch {
        Write-Error "Failed to download checksum from ${checksumUrl}: $_"
        exit 1
    }

    # --- Verify Checksum ---
    Write-Info "Verifying checksum..."
    # Match the filename token exactly: "aishell" is a prefix of "aishell-windows-amd64.exe".
    $expectedHash = $null
    foreach ($line in Get-Content $tmpSums) {
        $fields = $line.Trim() -split '\s+'
        if ($fields.Count -ge 2 -and $fields[1].TrimStart('*') -eq $asset) {
            $expectedHash = $fields[0].ToLower()
            break
        }
    }

    if (-not $expectedHash) {
        Write-Error "SHA256SUMS from ${checksumUrl} lists no entry for ${asset}"
        exit 1
    }

    $actualHash = (Get-FileHash $tmpAsset -Algorithm SHA256).Hash.ToLower()

    if ($actualHash -ne $expectedHash) {
        Write-Error "Checksum verification failed"
        Write-Host "  Expected: $expectedHash" -ForegroundColor Red
        Write-Host "  Got:      $actualHash" -ForegroundColor Red
        exit 1
    }

    # --- Install ---
    Write-Info "Installing..."
    try {
        Move-Item -Path $tmpAsset -Destination $destPath -Force
    } catch {
        Write-Error "Failed to install to ${destPath}: $_"
        exit 1
    }

    # Pre-4.1.0 installs put a bb-dependent script and its CMD shim here; both
    # are dead now that aishell.exe is on PATH. bb.exe is left alone - the old
    # installer may have put it there, and it is a tool in its own right.
    Remove-Item (Join-Path $installDir "aishell") -Force -ErrorAction SilentlyContinue
    Remove-Item (Join-Path $installDir "aishell.bat") -Force -ErrorAction SilentlyContinue
} finally {
    if ($null -ne $previousProgressPreference) {
        $ProgressPreference = $previousProgressPreference
    }
    Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
}

# --- PATH Management ---
$currentPath = [Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
$pathNeedsUpdate = $false

if ($currentPath -notlike "*$installDir*") {
    Write-Info "Adding $installDir to user PATH..."
    $newPath = "$currentPath;$installDir"
    [Environment]::SetEnvironmentVariable("Path", $newPath, [System.EnvironmentVariableTarget]::User)
    $pathNeedsUpdate = $true
    Write-Success "PATH updated"
} else {
    Write-Success "$installDir already in PATH"
}

# --- Success Message ---
Write-Host ""
Write-Success "Done! Installed aishell to $destPath"
Write-Host ""

if ($pathNeedsUpdate) {
    Write-Warn "Restart your terminal for PATH changes to take effect."
    Write-Host ""
    Write-Host "Then run:"
    Write-Host "  aishell setup --with-opencode   # Set up Docker image and select harnesses"
    Write-Host "  aishell opencode                # Run OpenCode"
} else {
    Write-Host "Quick start:"
    Write-Host "  aishell setup --with-opencode   # Set up Docker image and select harnesses"
    Write-Host "  aishell opencode                # Run OpenCode"
}
Write-Host ""
