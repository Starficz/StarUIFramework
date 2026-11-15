# StarUILib in-game test suite.
# Builds a TEST-ONLY copy of StarUILib.jar (src/ + native-smoke/kotlin/ compiled together with kotlinc), then
# runs it in an isolated Starsector through the starsector-native-smoke skill's runner, with StarUITestSupport.java
# (real engine input events) on the game classpath outside the security gate. The committed jars/StarUILib.jar
# is never touched.
#
# Usage (from anywhere):
#   powershell -NoProfile -ExecutionPolicy Bypass -File native-smoke\run-test-suite.ps1 [-Filter input,stack] [-OutDir D:\x\run-01]
# Exit code: the runner's (0 = every check passed). Results: <OutDir>\launcher.log (NATIVE_SMOKE lines), PNGs in <OutDir>.
#
# Preview mode (look at one piece of UI without running the suite):
#   ... run-test-suite.ps1 -Preview <name> [-PreviewSource my\Previews.kt] [-ExtraSources dirOrFile,dirOrFile]
# renders the preview registered under <name> (StarUITestSuite.preview("<name>") { ... }) and writes
# <OutDir>\<name>.png (screenshot) and <OutDir>\<name>.layout.txt (dumpLayout() text: kinds, sizes, positions).
# -PreviewSource replaces native-smoke\kotlin\Previews.kt (the file must define `fun registerPreviews()`);
# -ExtraSources adds more Kotlin files or folders to the build (your own UI code, if it only needs StarUI + the game).
param(
    [string]$Filter = '',
    [string]$Preview = '',
    [string]$PreviewSource = '',
    [string]$ExtraSources = '',
    [string]$OutDir = '',
    [string]$GameRoot = '',
    [string]$Resolution = '1280x800',
    [string]$JdkBin = 'C:\Program Files\Java\jdk-17\bin',
    [string]$KotlinHome = "$env:LOCALAPPDATA\Programs\IntelliJ IDEA Ultimate\plugins\Kotlin\kotlinc",
    [string]$Skill = "$env:USERPROFILE\.claude\skills\starsector-native-smoke",
    [int]$Timeout = 480
)
# Vanilla rendering on purpose: Fast Rendering moves OpenGL to its own render thread, so the harness's frame setup and
# glReadPixels (pixel checks, screenshots) have no context. FR saved only ~8 s of boot (39 s -> 31 s, 2026-11-06).
$ErrorActionPreference = 'Stop'
function Fail([string]$m) { [Console]::Error.WriteLine($m); exit 1 }

$Here = $PSScriptRoot
$Project = Split-Path -Parent $Here
$Mods = Split-Path -Parent $Project
if (-not $GameRoot) { $GameRoot = Split-Path -Parent $Mods }
$Core = Join-Path $GameRoot 'starsector-core'
if (-not $OutDir) { $OutDir = Join-Path $env:TEMP ("starui-tests\run-" + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$OutDir = [IO.Path]::GetFullPath($OutDir).TrimEnd('\')
$Work = "$OutDir-build"
if (Test-Path -LiteralPath $OutDir) { Fail "Output directory exists; pick a fresh one: $OutDir" }

$Runner = Join-Path $Skill 'scripts\run-native-smoke.ps1'
$Javac = Join-Path $JdkBin 'javac.exe'
$Java = Join-Path $JdkBin 'java.exe'
$JarExe = Join-Path $JdkBin 'jar.exe'
$KLib = Join-Path $KotlinHome 'lib'
foreach ($f in $Runner, $Javac, $Java, $JarExe, (Join-Path $KLib 'kotlin-compiler.jar'), (Join-Path $Core 'starfarer.api.jar')) {
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

$SupportSrc = @((Join-Path $Skill 'templates\NativeSmokeSupport.java'), (Join-Path $Skill 'templates\SmokePrefs.java'),
    (Join-Path $Here 'support\StarUITestSupport.java'))
New-Item -ItemType Directory -Force -Path "$Work\support", "$Work\classes" | Out-Null

Write-Host "[1/3] javac support helpers (compile-time view of the game-classpath helpers)"
& $Javac --release 17 -nowarn -encoding UTF-8 -cp ($GameJars -join ';') -d "$Work\support" @SupportSrc
if ($LASTEXITCODE) { Fail 'javac failed for support helpers' }

Write-Host "[2/3] kotlinc src + native-smoke/kotlin -> test-only StarUILib.jar"
$ktSources = @(Get-ChildItem -LiteralPath (Join-Path $Project 'src'), (Join-Path $Here 'kotlin') -Recurse -Filter *.kt -File | ForEach-Object { $_.FullName })
if ($PreviewSource) {
    if (-not (Test-Path -LiteralPath $PreviewSource -PathType Leaf)) { Fail "Preview source not found: $PreviewSource" }
    $ktSources = @($ktSources | Where-Object { (Split-Path -Leaf $_) -ne 'Previews.kt' }) + (Resolve-Path -LiteralPath $PreviewSource).Path
}
foreach ($extra in ($ExtraSources -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
    if (Test-Path -LiteralPath $extra -PathType Container) {
        $ktSources += @(Get-ChildItem -LiteralPath $extra -Recurse -Filter *.kt -File | ForEach-Object { $_.FullName })
    } elseif (Test-Path -LiteralPath $extra -PathType Leaf) {
        $ktSources += (Resolve-Path -LiteralPath $extra).Path
    } else { Fail "Extra source not found: $extra" }
}
$argFile = Join-Path $Work 'kotlinc.args'
$cp = (@("$Work\support") + $GameJars + $LibJars) -join ';'
$ktArgs = @('-no-stdlib', '-no-reflect', '-jvm-target', '1.8', '-api-version', '2.1', '-language-version', '2.2',
    '-module-name', 'StarUILib', '-nowarn', '-cp', $cp, '-d', "$Work\classes") + $ktSources
($ktArgs | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }) | Set-Content -Encoding ASCII -LiteralPath $argFile
& $Java -Xmx2g "-Dkotlin.home=$KotlinHome" -cp (Join-Path $KLib 'kotlin-preloader.jar') org.jetbrains.kotlin.preloading.Preloader `
    -cp (Join-Path $KLib 'kotlin-compiler.jar') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "@$argFile"
if ($LASTEXITCODE) { Fail 'kotlinc failed' }
$TestJar = Join-Path $Work 'StarUILib.jar'
& $JarExe --create --file $TestJar -C "$Work\classes" .
if ($LASTEXITCODE) { Fail 'jar failed' }

Write-Host "[3/3] native run -> $OutDir"
$saved = @{}
foreach ($n in 'SMOKE_MOD_JAR', 'SMOKE_SUPPORT_SOURCES', 'SMOKE_RESOLUTION', 'SMOKE_JAVAC_DIR', 'SMOKE_TIMEOUT', 'STARUI_TEST_FILTER', 'STARUI_PREVIEW') {
    $saved[$n] = [Environment]::GetEnvironmentVariable($n)
}
try {
    $env:SMOKE_MOD_JAR = $TestJar
    $env:SMOKE_SUPPORT_SOURCES = Join-Path $Here 'support\StarUITestSupport.java'
    $env:SMOKE_RESOLUTION = $Resolution
    $env:SMOKE_JAVAC_DIR = $JdkBin
    $env:SMOKE_TIMEOUT = "$Timeout"
    $env:STARUI_TEST_FILTER = $Filter
    $env:STARUI_PREVIEW = $Preview
    & powershell -NoProfile -ExecutionPolicy Bypass -File $Runner $GameRoot $Project (Join-Path $Here 'StarUITestSuiteProbe.java') $OutDir
    $code = $LASTEXITCODE
} finally {
    foreach ($n in $saved.Keys) { [Environment]::SetEnvironmentVariable($n, $saved[$n]) }
}

$log = Join-Path $OutDir 'launcher.log'
if (Test-Path -LiteralPath $log) {
    Write-Host ''
    Select-String -LiteralPath $log -Pattern 'NATIVE_SMOKE (FAIL|=== GROUP|=== PREVIEW|SUITE SUMMARY|support:)' | ForEach-Object { Write-Host $_.Line }
    Write-Host "PNGs: $OutDir"
}
if ($Preview) {
    $png = Join-Path $OutDir "$Preview.png"
    $layout = Join-Path $OutDir "$Preview.layout.txt"
    if (Test-Path -LiteralPath $layout) {
        Write-Host ''
        Write-Host "Screenshot: $png"
        Write-Host "Layout:     $layout"
        Get-Content -LiteralPath $layout | ForEach-Object { Write-Host $_ }
    }
}
exit $code
