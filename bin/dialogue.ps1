# =============================================================================
# xiaozhi-dialogue 管理脚本 (Windows PowerShell)
# 用法: bin\dialogue.ps1 <start|stop|restart|status> [dev|prod]，运行环境默认 dev
# =============================================================================
param([string]$Action, [string]$SpringProfile)

. (Join-Path $PSScriptRoot '_common.ps1')

$Name   = 'xiaozhi-dialogue'
$Module = 'xiaozhi-dialogue'
$Port   = 8092

switch ($Action) {
    'start' {
        $profileName = Resolve-Profile $SpringProfile
        if (-not (Invoke-Preflight)) { exit 1 }
        Invoke-Build $Module
        Start-XzService $Name $Module $Port $profileName
    }
    'stop' {
        Stop-XzService $Name
    }
    'restart' {
        $profileName = Resolve-Profile $SpringProfile
        if (-not (Invoke-Preflight)) { exit 1 }
        Stop-XzService $Name
        Start-Sleep -Seconds 1
        Invoke-Build $Module
        Start-XzService $Name $Module $Port $profileName
    }
    'status' {
        Get-XzServiceStatus $Name $Port
    }
    default {
        Show-Usage 'bin\dialogue.ps1'
        exit 1
    }
}
