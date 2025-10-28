# PowerShell equivalent of cleanup.sh
# Removes the built jar and target directory from the template_java project

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$jarPath = Join-Path $scriptDir "bin\da_proc.jar"
$targetDir = Join-Path $scriptDir "target"

if (Test-Path $jarPath) {
    Remove-Item $jarPath -Force
}

if (Test-Path $targetDir) {
    Remove-Item $targetDir -Recurse -Force
}
