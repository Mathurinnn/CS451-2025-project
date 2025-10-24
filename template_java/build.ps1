# build.ps1 - Build da_proc.jar for Windows PowerShell
# Usage: .\build.ps1
# This script will try to use Maven (if available) to build the fat JAR. If Maven
# is not present, it falls back to compiling .java files with javac and packing
# classes into bin\da_proc.jar with the `jar` tool.

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

function Exit-WithError([string]$msg, [int]$code = 1) {
    Write-Error $msg
    exit $code
}

Write-Host "Building project in: $ScriptDir"

# Ensure bin directory exists
if (-not (Test-Path "$ScriptDir\bin")) {
    New-Item -ItemType Directory -Path "$ScriptDir\bin" | Out-Null
}

# Check for Maven
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnCmd) {
    Write-Host "Maven detected. Running 'mvn clean compile assembly:single'..."
    & mvn clean compile assembly:single
    if ($LASTEXITCODE -ne 0) {
        Exit-WithError "Maven build failed with exit code $LASTEXITCODE" $LASTEXITCODE
    }

    # find the assembly jar (jar-with-dependencies)
    $assembled = Get-ChildItem -Path "$ScriptDir\target" -Recurse -Filter "*-jar-with-dependencies.jar" | Select-Object -First 1
    if (-not $assembled) {
        Exit-WithError "Built JAR not found in target/. Make sure assembly plugin produced a jar." 2
    }

    $dest = Join-Path $ScriptDir "bin\da_proc.jar"
    Copy-Item -Path $assembled.FullName -Destination $dest -Force
    if ($?) {
        Write-Host "Created $dest"
        exit 0
    } else {
        Exit-WithError "Failed to copy assembled JAR to $dest" 3
    }
}

Write-Host "Maven not detected. Falling back to javac + jar approach..."

# Ensure target/classes exists
$targetClasses = Join-Path $ScriptDir "target\classes"
if (-not (Test-Path $targetClasses)) { New-Item -ItemType Directory -Path $targetClasses -Force | Out-Null }

# Collect java sources
$srcDir = Join-Path $ScriptDir "src\main\java"
if (-not (Test-Path $srcDir)) { Exit-WithError "Source directory not found: $srcDir" 4 }

$javaFiles = Get-ChildItem -Path $srcDir -Recurse -Include *.java | ForEach-Object { $_.FullName }
if (-not $javaFiles) { Exit-WithError "No Java source files found under $srcDir" 5 }

# Compile
Write-Host "Compiling Java sources..."
& javac -d "$targetClasses" -cp "$targetClasses" $javaFiles
if ($LASTEXITCODE -ne 0) { Exit-WithError "javac failed with exit code $LASTEXITCODE" $LASTEXITCODE }

# Create jar
Push-Location $targetClasses
try {
    $destJar = Join-Path $ScriptDir "bin\da_proc.jar"
    Write-Host "Packaging classes into $destJar (Main class: cs451.Main)..."
    & jar cfe $destJar cs451.Main cs451\*.class
    if ($LASTEXITCODE -ne 0) { Exit-WithError "jar command failed with exit code $LASTEXITCODE" $LASTEXITCODE }
    Write-Host "Created $destJar"
} finally {
    Pop-Location
}

exit 0
