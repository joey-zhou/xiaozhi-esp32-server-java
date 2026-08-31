# =============================================================================
# xiaozhi-server 管理脚本 (Windows PowerShell)
# 用法: bin\server.ps1 <start|stop|restart|status>
# =============================================================================
param([string]$Action)

. (Join-Path $PSScriptRoot '_common.ps1')

$Name   = 'xiaozhi-server'
$Module = 'xiaozhi-server'
$Port   = 8091

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
        Show-Usage 'bin\server.ps1'
        exit 1
    }
}
