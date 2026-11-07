param([string]$EnvironmentPath = '.tools/vision-venv', [string]$PythonExe = 'python', [switch]$ReuseInstalledTorch)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$env:PIP_CACHE_DIR = Join-Path $projectRoot '.tools/pip-cache'
$env:HF_HOME = Join-Path $projectRoot '.tools/hf-home'
$env:HF_HUB_DISABLE_XET = '1'
$env:HF_HUB_DISABLE_TELEMETRY = '1'
$env:DO_NOT_TRACK = '1'
$env:HF_HUB_DOWNLOAD_TIMEOUT = '120'
$env:PYTHONUTF8 = '1'
$env:PIP_DEFAULT_TIMEOUT = '120'
$env:PIP_RETRIES = '8'
$env:MKL_THREADING_LAYER = 'SEQUENTIAL'
if ($PythonExe -ne 'python') {
    $baseEnvironment = Split-Path $PythonExe -Parent
    $env:PATH = $baseEnvironment + ';' + (Join-Path $baseEnvironment 'Library/bin') + ';' + $env:PATH
}
$pythonPath = Join-Path $EnvironmentPath 'Scripts/python.exe'
if (-not (Test-Path -LiteralPath $pythonPath)) {
    if ($ReuseInstalledTorch) { & $PythonExe -m venv --system-site-packages $EnvironmentPath } else { & $PythonExe -m venv $EnvironmentPath }
    if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated environment' }
}
& $pythonPath -m ensurepip --upgrade --default-pip
if ($LASTEXITCODE -ne 0) { throw 'Could not initialize pip' }
& $pythonPath -m pip install pip==25.3
if ($LASTEXITCODE -ne 0) { throw 'pip resume-capable upgrade failed' }
if (-not $ReuseInstalledTorch) {
    & $pythonPath -m pip install torch==2.8.0 torchvision==0.23.0 --index-url https://download.pytorch.org/whl/cu126
    if ($LASTEXITCODE -ne 0) { throw 'PyTorch installation failed' }
}
& $pythonPath -m pip install -r vision/requirements.txt
if ($LASTEXITCODE -ne 0) { throw 'Vision dependencies installation failed' }
& $pythonPath vision/scripts/download_model.py
if ($LASTEXITCODE -ne 0) { throw 'Model weight download failed' }
