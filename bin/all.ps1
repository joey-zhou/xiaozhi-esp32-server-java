# =============================================================================
# 所有服务管理脚本（server + dialogue） (Windows PowerShell)
# 用法: bin\all.ps1 <start|stop|restart|status>
# =============================================================================
param([string]$Action)

. (Join-Path $PSScriptRoot '_common.ps1')

switch ($Action) {
    'start' {
        Invoke-Build all
        Start-XzService 'xiaozhi-server'   'xiaozhi-server'   8091
        Start-XzService 'xiaozhi-dialogue' 'xiaozhi-dialogue' 8092
        Write-Host ''
        Write-XzOk '全部启动完成'
    }
    'stop' {
        Stop-XzService 'xiaozhi-server'
        Stop-XzService 'xiaozhi-dialogue'
        Write-XzOk '全部已停止'
    }
    'restart' {
        Stop-XzService 'xiaozhi-server'
        Stop-XzService 'xiaozhi-dialogue'
        Start-Sleep -Seconds 1
        Invoke-Build all
        Start-XzService 'xiaozhi-server'   'xiaozhi-server'   8091
        Start-XzService 'xiaozhi-dialogue' 'xiaozhi-dialogue' 8092
        Write-Host ''
        Write-XzOk '全部重启完成'
    }
    'status' {
        Write-Host ''
        Get-XzServiceStatus 'xiaozhi-server'   8091
        Get-XzServiceStatus 'xiaozhi-dialogue' 8092
        Write-Host ''
    }
    default {
        Write-Host '用法: bin\all.ps1 <start|stop|restart|status>'
        exit 1
    }
}
