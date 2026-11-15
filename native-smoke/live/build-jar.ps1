# Builds a TEST-ONLY StarUILib.jar for the scripted campaign checks (smoke.sh checks, see project.sh):
# src/ and native-smoke/live/kotlin/ (small Java-friendly helpers the check mod calls) compiled together with kotlinc.
# The committed jars/StarUILib.jar is never touched.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File native-smoke\live\build-jar.ps1 -OutJar D:\x\StarUILib.jar
param(
    [Parameter(Mandatory)][string]$OutJar,
    [string]$GameRoot = '',
    [string]$JdkBin = 'C:\Program Files\Java\jdk-17\bin',
    [string]$KotlinHome = "$env:LOCALAPPDATA\Programs\IntelliJ IDEA Ultimate\plugins\Kotlin\kotlinc"
)
$ErrorActionPreference = 'Stop'
function Fail([string]$m) { [Console]::Error.WriteLine($m); exit 1 }

$Here = $PSScriptRoot
$Project = Split-Path -Parent (Split-Path -Parent $Here)
$Mods = Split-Path -Parent $Project
if (-not $GameRoot) { $GameRoot = Split-Path -Parent $Mods }
$Core = Join-Path $GameRoot 'starsector-core'
$Java = Join-Path $JdkBin 'java.exe'
$JarExe = Join-Path $JdkBin 'jar.exe'
$KLib = Join-Path $KotlinHome 'lib'
foreach ($f in $Java, $JarExe, (Join-Path $KLib 'kotlin-compiler.jar'), (Join-Path $Core 'starfarer.api.jar')) {
    if (-not (Test-Path -LiteralPath $f)) { Fail "Missing prerequisite: $f" }
}

function Find-ModFolder([string]$id) {
    foreach ($d in Get-ChildItem -LiteralPath $Mods -Directory) {
        $info = Join-Path $d.FullName 'mod_info.json'
        if (-not (Test-Path -LiteralPath $info)) { continue }
        # drop the dependencies block so a mod that merely depends on $id does not match
        $text = [regex]::Replace((Get-Content -Raw -LiteralPath $info), '"dependencies"\s*:\s*\[.*?\]', '', 'Singleline')
        if ($text -match ('"id"\s*:\s*"' + [regex]::Escape($id) + '"')) { return $d.FullName }
    }
    Fail "Mod '$id' not found in $Mods"
}
$LazyLib = Find-ModFolder 'lw_lazylib'
$PatchLib = Find-ModFolder 'patchlib'

$GameJars = @('starfarer.api.jar', 'starfarer_obf.jar', 'fs.common_obf.jar', 'fs.sound_obf.jar', 'lwjgl.jar', 'lwjgl_util.jar',
    'log4j-1.2.9.jar', 'json.jar', 'xstream-1.4.10.jar') | ForEach-Object { Join-Path $Core $_ }
# Kotlin stdlib comes from LazyLib at runtime (2.1.20), so compile against it with api-version 2.1.
$LibJars = @((Join-Path $LazyLib 'jars\LazyLib.jar'), (Join-Path $LazyLib 'jars\LazyLib-Kotlin.jar'),
    (Join-Path $LazyLib 'jars\internal\Kotlin-Runtime.jar'), (Join-Path $PatchLib 'jars\PatchLibAPI.jar'))
foreach ($f in $GameJars + $LibJars) { if (-not (Test-Path -LiteralPath $f)) { Fail "Missing jar: $f" } }

$OutJar = [IO.Path]::GetFullPath($OutJar)
$Work = Join-Path (Split-Path -Parent $OutJar) 'starui-live-build'
if (Test-Path -LiteralPath $Work) { Remove-Item -LiteralPath $Work -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$Work\classes" | Out-Null

$sources = @((Join-Path $Project 'src'), (Join-Path $Here 'kotlin') | ForEach-Object {
    Get-ChildItem -LiteralPath $_ -Recurse -Filter *.kt -File } | ForEach-Object { $_.FullName })
$argFile = Join-Path $Work 'kotlinc.args'
$ktArgs = @('-no-stdlib', '-no-reflect', '-jvm-target', '1.8', '-api-version', '2.1', '-language-version', '2.2',
    '-module-name', 'StarUILib', '-nowarn', '-cp', (($GameJars + $LibJars) -join ';'), '-d', "$Work\classes") + $sources
($ktArgs | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }) | Set-Content -Encoding ASCII -LiteralPath $argFile
Write-Host "kotlinc StarUILib + live helpers ($($sources.Count) files)"
& $Java -Xmx2g "-Dkotlin.home=$KotlinHome" -cp (Join-Path $KLib 'kotlin-preloader.jar') org.jetbrains.kotlin.preloading.Preloader `
    -cp (Join-Path $KLib 'kotlin-compiler.jar') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "@$argFile"
if ($LASTEXITCODE) { Fail 'kotlinc failed' }
if (Test-Path -LiteralPath $OutJar) { Remove-Item -LiteralPath $OutJar -Force }
& $JarExe --create --file $OutJar -C "$Work\classes" .
if ($LASTEXITCODE) { Fail 'jar failed' }
Write-Host "built $OutJar"
