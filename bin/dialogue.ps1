# =============================================================================
# xiaozhi-dialogue 管理脚本 (Windows PowerShell)
# 用法: bin\dialogue.ps1 <start|stop|restart|status>
# =============================================================================
param([string]$Action)

. (Join-Path $PSScriptRoot '_common.ps1')

$Name   = 'xiaozhi-dialogue'
$Module = 'xiaozhi-dialogue'
$Port   = 8092

switch ($Action) {
    'start' {
        Invoke-Build $Module
        Start-XzService $Name $Module $Port
    }
    'stop' {
        Stop-XzService $Name
    }
    'restart' {
        Stop-XzService $Name
        Start-Sleep -Seconds 1
        Invoke-Build $Module
        Start-XzService $Name $Module $Port
    }
    'status' {
        Get-XzServiceStatus $Name $Port
    }
    default {
        Show-Usage 'bin\dialogue.ps1'
        exit 1
    }
}
