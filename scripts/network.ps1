<#
.SYNOPSIS
    Controls the internet of an Android emulator or phone, to see how the app reacts.

.EXAMPLE
    .\scripts\network.ps1 status
    .\scripts\network.ps1 offline
    .\scripts\network.ps1 drop -AfterSeconds 3     # run it, then tap Retry in the app
    .\scripts\network.ps1 reset

.NOTES
    status   what the device and the app think of the network
    offline  airplane mode on
    online   airplane mode off, then waits for real internet
    slow     EDGE speed and 5 s latency: loads, after the app says it is slow   (emulator only)
    timeout  GSM speed and 20 s latency: requests time out                      (emulator only)
    drop     waits -AfterSeconds, then airplane mode on: lost mid-request
    reset    full speed, no latency, airplane mode off

    Wi-Fi and mobile data switches are never touched. Needs Android 11 or newer; no root.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('status', 'offline', 'online', 'slow', 'timeout', 'drop', 'reset')]
    [string] $Action,

    # Only needed when several devices are connected, e.g. emulator-5554
    [string] $Device,

    [ValidateRange(0, 120)]
    [int] $AfterSeconds = 3
)

$ErrorActionPreference = 'Stop'

# ---- adb ---------------------------------------------------------------------------------------

function Find-Adb {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $sdks = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $localProperties = Join-Path $PSScriptRoot '..\local.properties'
    if (Test-Path $localProperties) {
        $line = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) { $sdks += ($line -replace '^sdk\.dir=', '') -replace '\\(.)', '$1' }   # E\:\\sdk -> E:\sdk
    }
    $sdks += "$env:LOCALAPPDATA\Android\Sdk"

    foreach ($sdk in $sdks) {
        if (-not $sdk) { continue }
        $adb = Join-Path $sdk 'platform-tools\adb.exe'
        if (Test-Path $adb) { return $adb }
    }
    throw 'adb was not found. Set ANDROID_HOME, or sdk.dir in local.properties.'
}

function Select-Device([string] $wanted) {
    $devices = @(& $adb devices | Where-Object { $_ -match '\tdevice$' } | ForEach-Object { ($_ -split '\t')[0] })
    if ($devices.Count -eq 0) { throw 'No device is connected. Start an emulator or plug in a phone.' }
    if ($wanted) {
        if ($devices -notcontains $wanted) { throw "'$wanted' is not connected. Connected: $($devices -join ', ')" }
        return $wanted
    }
    if ($devices.Count -gt 1) { throw "Several devices are connected. Choose one with -Device: $($devices -join ', ')" }
    return $devices[0]
}

function Invoke-Shell([string] $command) {
    $output = & $adb -s $serial shell $command
    if ($LASTEXITCODE -ne 0) { throw "Failed on ${serial}: $command" }
    return ($output -join "`n").Trim()
}

function Invoke-Console([string[]] $command) {
    $output = (& $adb -s $serial emu @command) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $output -match '(?m)^KO') { throw "The emulator refused '$command': $output" }
    return $output
}

# ---- Checks before changing anything -----------------------------------------------------------

function Assert-Emulator {
    if (-not $isEmulator) { throw "'$Action' only works on an emulator; $serial is a phone. Use a throttled hotspot instead." }
}

function Assert-CanGoOffline {
    if ([int](Invoke-Shell 'getprop ro.build.version.sdk') -lt 30) { throw 'Airplane mode from adb needs Android 11 or newer.' }
    if ($serial -match ':\d+$|_adb-tls') { throw "$serial is connected over Wi-Fi: going offline would cut adb off. Use a USB cable." }
}

# ---- Reading -----------------------------------------------------------------------------------

function Test-Airplane { return (Invoke-Shell 'settings get global airplane_mode_on') -eq '1' }

# Real internet, not just a connection: the same VALIDATED flag AndroidConnectivityMonitor reads.
function Test-Internet {
    $dump = Invoke-Shell "dumpsys connectivity | grep -E 'Active default network:|NetworkAgentInfo\{network'"
    if ($dump -notmatch 'Active default network: (\d+)') { return $false }
    $id = $Matches[1]
    foreach ($line in ($dump -split "`n")) {
        if ($line -match "network\{$id\}" -and $line -cmatch 'Capabilities: (\S+)') {
            return ($Matches[1] -split '&') -ccontains 'VALIDATED'
        }
    }
    return $false
}

# $stableSeconds: how long it must stay that way before we believe it.
function Wait-Internet([bool] $expected, [int] $seconds, [int] $stableSeconds = 0) {
    $deadline = (Get-Date).AddSeconds($seconds)
    $since = $null
    do {
        if ((Test-Internet) -ne $expected) { $since = $null }
        else {
            if (-not $since) { $since = Get-Date }
            if (((Get-Date) - $since).TotalSeconds -ge $stableSeconds) { return }
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    $wanted = if ($expected) { 'online' } else { 'offline' }
    throw "$serial is still not $wanted after $seconds s. Check it with: .\scripts\network.ps1 status"
}

function Show-Status {
    $onOff = { param($value) if ($value -eq '0') { 'off' } else { 'on' } }
    Write-Host "Device         $serial"
    Write-Host "Internet       $(if (Test-Internet) { 'yes' } else { 'NO' })"
    Write-Host "Airplane mode  $(if (Test-Airplane) { 'ON' } else { 'off' })"
    Write-Host "Wi-Fi          $(& $onOff (Invoke-Shell 'settings get global wifi_on'))"
    Write-Host "Mobile data    $(& $onOff (Invoke-Shell 'settings get global mobile_data'))"
    if ($isEmulator) {
        $shape = Invoke-Console 'network', 'status'
        $speed = [regex]::Match($shape, 'download speed:\s+(\d+)').Groups[1].Value
        $delay = [regex]::Match($shape, 'maximum latency:\s+(\d+)').Groups[1].Value
        Write-Host "Speed limit    $(if ($speed -eq '0') { 'none' } else { "$speed bits/s" })"
        Write-Host "Added latency  $(if ($delay -eq '0') { 'none' } else { "up to $delay ms" })"
    }
    $lastLine = (Invoke-Shell 'logcat -d -s Connectivity:D') -split "`n" | Where-Object { $_ -match 'tatus:' } | Select-Object -Last 1
    Write-Host "The app saw    $(if ($lastLine) { $lastLine.Trim() } else { 'nothing yet (open the app; dev builds only)' })"
}

# ---- Changing ----------------------------------------------------------------------------------

function Enter-Offline {
    Invoke-Shell 'cmd connectivity airplane-mode enable' | Out-Null
    Wait-Internet $false 15
    Write-Host 'Offline (airplane mode on).'
}

function Exit-Offline([int] $stableSeconds = 0) {
    Invoke-Shell 'cmd connectivity airplane-mode disable' | Out-Null
    Wait-Internet $true 60 $stableSeconds
    Write-Host 'Online.'
}

function Set-Shape([string] $speed, [string] $delay) {
    Invoke-Console 'network', 'speed', $speed | Out-Null
    Invoke-Console 'network', 'delay', $delay | Out-Null
}

# ---- Main --------------------------------------------------------------------------------------

$adb = Find-Adb
$serial = Select-Device $Device
$isEmulator = $serial -like 'emulator-*'

switch ($Action) {
    'status' { Show-Status }
    'offline' { Assert-CanGoOffline; Enter-Offline }
    'online' { Assert-CanGoOffline; Exit-Offline }
    'slow' {
        Assert-Emulator
        Set-Shape 'edge' '5000'     # EDGE's own latency (200 ms) is too little: the app needs over 3 s to call it slow
        Write-Host 'Slow: EDGE speed, 5 s latency. Undo with: reset'
    }
    'timeout' {
        Assert-Emulator
        Set-Shape 'gsm' '20000'
        Write-Host 'Requests will time out: GSM speed, 20 s latency. Undo with: reset'
    }
    'drop' {
        Assert-CanGoOffline
        Write-Host "Going offline in $AfterSeconds s: start the request now."
        Start-Sleep -Seconds $AfterSeconds
        Enter-Offline
    }
    'reset' {
        Assert-CanGoOffline
        if ($isEmulator) { Set-Shape 'full' 'none' }    # first: 20 s of latency would fail the wait below
        Exit-Offline -stableSeconds 10                  # after "timeout", Android can still drop to offline once
    }
}
