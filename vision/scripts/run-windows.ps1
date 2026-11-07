[CmdletBinding(PositionalBinding=$false)]
param(
    [string]$EnvironmentPath = '.tools/vision-venv',
    [Parameter(Position=0, ValueFromRemainingArguments=$true)][string[]]$ModelArguments
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $ProjectRoot
if (!(Test-Path "$EnvironmentPath/Scripts/python.exe") -and (Test-Path '.tools/vision-venv311/Scripts/python.exe')) {
    $EnvironmentPath = '.tools/vision-venv311'
}
$PythonPath = Join-Path $EnvironmentPath 'Scripts/python.exe'
if (!(Test-Path $PythonPath)) { throw 'Run setup-windows.ps1 first, or pass -EnvironmentPath.' }
$VenvConfig = Get-Content (Join-Path $EnvironmentPath 'pyvenv.cfg')
$HomeLine = $VenvConfig | Where-Object { $_ -match '^home = ' } | Select-Object -First 1
if ($HomeLine) {
    $BaseEnvironment = $HomeLine.Substring(7).Trim()
    $env:PATH = "$BaseEnvironment;$BaseEnvironment/Library/bin;$BaseEnvironment/Scripts;" + $env:PATH
}
$env:MKL_THREADING_LAYER = 'SEQUENTIAL'
$env:PYTHONUTF8 = '1'
$env:PYTHONPATH = Join-Path $ProjectRoot 'vision'
$env:HF_HUB_OFFLINE = '1'
$env:TRANSFORMERS_OFFLINE = '1'
if ($ModelArguments.Count -gt 0 -and $ModelArguments[0] -eq 'smoke') {
    $SmokeArguments = @($ModelArguments | Select-Object -Skip 1)
    & $PythonPath vision/scripts/smoke_model.py @SmokeArguments
} else {
    & $PythonPath -m framecoach_vision.cli @ModelArguments
}
exit $LASTEXITCODE
