$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSCommandPath
$classes = Join-Path $projectRoot 'target\server-manager-classes'
$logDirectory = Join-Path $env:LOCALAPPDATA 'MyMusic'
$outputLog = Join-Path $logDirectory 'server-manager.log'
$errorLog = Join-Path $logDirectory 'server-manager-error.log'

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

try {
    try {
        $status = Invoke-RestMethod -Uri 'http://127.0.0.1:8088/api/control/status' -TimeoutSec 2
        if ($status.controlPort -eq 8088) { exit 0 }
    } catch {
        # Start the manager when its control endpoint is unavailable.
    }

    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    $compiler = (Get-Command javac.exe -ErrorAction Stop).Source
    & $compiler -encoding UTF-8 -d $classes (Join-Path $projectRoot 'tools\ServerManager.java')
    if ($LASTEXITCODE -ne 0) { throw "Manager compilation failed with exit code $LASTEXITCODE" }

    $java = (Get-Command javaw.exe -ErrorAction Stop).Source
    $arguments = "-Djava.awt.headless=true -cp `"$classes`" tools.ServerManager --headless"
    $manager = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $projectRoot `
        -WindowStyle Hidden -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog -PassThru

    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Milliseconds 300
        if ($manager.HasExited) { throw "Manager exited with code $($manager.ExitCode)" }
        try {
            $status = Invoke-RestMethod -Uri 'http://127.0.0.1:8088/api/control/status' -TimeoutSec 1
            if ($status.controlPort -eq 8088) { exit 0 }
        } catch {
            # The control listener may still be starting.
        }
    }
    throw 'Manager did not open port 8088 after startup'
} catch {
    Add-Content -Path $errorLog -Value "$(Get-Date -Format o) Startup failed: $_"
    exit 1
}
