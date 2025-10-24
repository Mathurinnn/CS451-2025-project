# PowerShell script to run the Java distributed algorithm project
# Usage: .\run.ps1 --id 1 --hosts ..\example\hosts --output ..\example\output\1.output ..\example\configs\perfect-links.config

$DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$ret = 0

try {
    java -jar "$DIR\bin\da_proc.jar" $args
    $ret = $LASTEXITCODE
} catch {
    $ret = 1
}

exit $ret
