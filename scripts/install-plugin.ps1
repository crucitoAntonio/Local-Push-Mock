<#
  Local Push Mock plugin installer for Windows.

  Does all of "Part A" of the user guide:
    1. Detects Android Studio installations.
    2. Uses the Java 21 bundled with Android Studio (JBR): no JDK install needed.
    3. Builds the plugin with the bundled Gradle Wrapper: no Gradle install needed.
    4. Copies the plugin into the plugins folder of every Android Studio found.

  Usage (or double-click install-plugin.cmd):
    .\install-plugin.ps1                   build and install
    .\install-plugin.ps1 -Zip plugin.zip   install a prebuilt .zip
    .\install-plugin.ps1 -Uninstall        uninstall
    .\install-plugin.ps1 -Dest <folder>    install into a specific plugins folder
#>
param(
    [string]$Zip,
    [string]$Dest,
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$PluginProject = Join-Path $Root 'plugin'
$PluginFolder = 'local-push-mock'
$MinJava = 21

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor White }
function Ok($msg)   { Write-Host "  [ok]    $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "  [warn]  $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "`n  [error] $msg" -ForegroundColor Red; exit 1 }

function Get-JavaMajor($jdkHome) {
    $release = Join-Path $jdkHome 'release'
    if (-not (Test-Path $release)) { return 0 }
    $m = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($m) { return [int]$m.Matches[0].Groups[1].Value } else { return 0 }
}

# ------------------------------------------------------------ Android Studio

Step 'Looking for Android Studio'
$candidates = @(
    "$env:ProgramFiles\Android\Android Studio*",
    "${env:ProgramFiles(x86)}\Android\Android Studio*",
    "$env:LOCALAPPDATA\Programs\Android Studio*",
    "$env:LOCALAPPDATA\JetBrains\Toolbox\apps\AndroidStudio\*\*"
)
$installs = @($candidates |
    ForEach-Object { Get-Item -Path $_ -ErrorAction SilentlyContinue } |
    Where-Object { Test-Path (Join-Path $_.FullName 'product-info.json') } |
    Select-Object -ExpandProperty FullName -Unique)

$targets = @()
if ($Dest) {
    $targets = @($Dest)
    Ok "Manual destination: $Dest"
} else {
    foreach ($install in $installs) {
        $info = Get-Content (Join-Path $install 'product-info.json') -Raw | ConvertFrom-Json
        if (-not $info.dataDirectoryName) { continue }
        $targets += Join-Path $env:APPDATA "Google\$($info.dataDirectoryName)\plugins"
        Ok "$($info.dataDirectoryName)  ($install)"
    }
    if ($targets.Count -eq 0) { Fail 'Android Studio not found. Install it or pass the plugins folder with -Dest <folder>.' }
}

# ------------------------------------------------------------ uninstall

if ($Uninstall) {
    Step 'Uninstalling'
    foreach ($dir in $targets) {
        $p = Join-Path $dir $PluginFolder
        if (Test-Path $p) { Remove-Item $p -Recurse -Force; Ok "Removed from $dir" }
        else { Warn "Not installed in $dir" }
    }
    Write-Host "`n==> Done. Restart Android Studio." -ForegroundColor Green
    exit 0
}

# ------------------------------------------------------------ build

if (-not $Zip) {
    Step "Looking for Java $MinJava+"
    $java = $null
    if ($env:JAVA_HOME -and (Get-JavaMajor $env:JAVA_HOME) -ge $MinJava) {
        $java = $env:JAVA_HOME
    } else {
        foreach ($install in $installs) {
            $jbr = Join-Path $install 'jbr'
            if ((Get-JavaMajor $jbr) -ge $MinJava) { $java = $jbr; break }
        }
    }
    if (-not $java) { Fail "No Java $MinJava+ found. Update Android Studio (Ladybug or later) or set JAVA_HOME." }
    Ok "Java $(Get-JavaMajor $java): $java"

    Step 'Building the plugin (the first run downloads dependencies and may take a few minutes)'
    $env:JAVA_HOME = $java
    Push-Location $PluginProject
    try {
        & .\gradlew.bat buildPlugin --console=plain --warning-mode=none
        if ($LASTEXITCODE -ne 0) { Fail 'Build failed (see the error above).' }
    } finally { Pop-Location }

    $Zip = Get-ChildItem (Join-Path $PluginProject 'build\distributions') -Filter "$PluginFolder-*.zip" |
        Where-Object { $_.Name -notlike '*-signed.zip' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $Zip) { Fail 'The plugin .zip was not generated.' }
    Ok "Built: $Zip"
}

if (-not (Test-Path $Zip)) { Fail "File not found: $Zip" }

# ------------------------------------------------------------ install

Step 'Installing'
foreach ($dir in $targets) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $p = Join-Path $dir $PluginFolder
    if (Test-Path $p) { Remove-Item $p -Recurse -Force }   # replaces previous versions
    Expand-Archive -Path $Zip -DestinationPath $dir -Force
    if (-not (Test-Path (Join-Path $p 'lib'))) { Fail "The .zip does not have the expected layout ($PluginFolder\lib)." }
    Ok "Installed in $dir"
}

Write-Host "`n==> Plugin installed." -ForegroundColor Green
if (Get-Process -Name studio64, studio -ErrorAction SilentlyContinue) {
    Warn 'Android Studio is running: quit and reopen it to load the plugin.'
} else {
    Ok 'Open Android Studio: the "Local Push" tool window is on the right side.'
}
