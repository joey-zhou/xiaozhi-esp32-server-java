# =============================================================================
# 公共函数库，被 server.ps1 / dialogue.ps1 / all.ps1 引用，不直接执行
# Windows PowerShell 版本，与 _common.sh 功能对应
# =============================================================================

$ErrorActionPreference = 'Stop'

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$LogsDir = Join-Path $RootDir 'logs'

# ---- 日志输出 ----
function Write-XzLog  { param([string]$Msg) Write-Host "[xiaozhi] $Msg" -ForegroundColor Green }
function Write-XzInfo { param([string]$Msg) Write-Host "[xiaozhi] $Msg" -ForegroundColor Cyan }
function Write-XzWarn { param([string]$Msg) Write-Host "[xiaozhi] $Msg" -ForegroundColor Yellow }
function Write-XzErr  { param([string]$Msg) Write-Host "[xiaozhi] $Msg" -ForegroundColor Red }
function Write-XzOk   { param([string]$Msg) Write-Host "[xiaozhi] $Msg" -ForegroundColor Green }

# ---- 部署模式检测 ----
# 部署模式：$RootDir 下没有 pom.xml（纯 jar 部署）或没有 mvn 命令
# 此时跳过编译，直接使用现成的 jar
function Test-DeployMode {
    if (-not (Test-Path (Join-Path $RootDir 'pom.xml'))) { return $true }
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { return $true }
    return $false
}

# ---- Java 可执行文件解析 ----
# 优先级: $env:JAVA_BIN > $env:JAVA_HOME\bin\java.exe > PATH 中的 java
function Resolve-Java {
    if ($env:JAVA_BIN -and (Test-Path $env:JAVA_BIN)) {
        return $env:JAVA_BIN
    }
    if ($env:JAVA_HOME) {
        $jh = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $jh) { return $jh }
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

# ---- 编译 ----
# Invoke-Build <module>  — 只编译该模块及其依赖
# Invoke-Build all       — 编译全部
function Invoke-Build {
    param([string]$Target = 'all')

    if (Test-DeployMode) {
        Write-XzInfo '部署模式：跳过编译（未检测到 pom.xml 或 mvn 命令）'
        return
    }

    $pom = Join-Path $RootDir 'pom.xml'
    if ($Target -eq 'all') {
        Write-XzInfo '编译所有模块...'
        & mvn clean install -DskipTests -q -f $pom
    } else {
        Write-XzInfo "编译 $Target 及其依赖..."
        & mvn clean install -DskipTests -q -f $pom -pl $Target --also-make
    }
    if ($LASTEXITCODE -ne 0) { throw "Maven 编译失败 (exit=$LASTEXITCODE)" }
    Write-XzLog '编译完成'
}

# ---- 查找 jar ----
# xiaozhi-dialogue 使用 classifier=exec，产出 *-exec.jar；其余模块用普通 jar
# 优先在 $RootDir 根目录查找（部署模式），找不到再回退到 $module\target\（开发模式）
function Find-Jar {
    param([string]$Module)

    if ($Module -eq 'xiaozhi-dialogue') {
        $jar = Get-ChildItem -Path (Join-Path $RootDir "$Module-*-exec.jar") -ErrorAction SilentlyContinue |
               Select-Object -First 1
        if (-not $jar) {
            $jar = Get-ChildItem -Path (Join-Path $RootDir "$Module\target\$Module-*-exec.jar") -ErrorAction SilentlyContinue |
                   Select-Object -First 1
        }
    } else {
        $jar = Get-ChildItem -Path (Join-Path $RootDir "$Module-*.jar") -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -notmatch 'original' -and $_.Name -notmatch '-exec\.jar$' } |
               Select-Object -First 1
        if (-not $jar) {
            $jar = Get-ChildItem -Path (Join-Path $RootDir "$Module\target\$Module-*.jar") -ErrorAction SilentlyContinue |
                   Where-Object { $_.Name -notmatch 'original' -and $_.Name -notmatch '-exec\.jar$' } |
                   Select-Object -First 1
        }
    }
    if ($jar) { return $jar.FullName }
    return $null
}

# ---- PID 文件路径 ----
function Get-PidFile {
    param([string]$Name)
    return (Join-Path $LogsDir "$Name.pid")
}

# ---- 判断进程是否存活 ----
function Test-ServiceRunning {
    param([string]$Name)
    $pidPath = Get-PidFile $Name
    if (-not (Test-Path $pidPath)) { return $false }
    $procId = (Get-Content $pidPath -ErrorAction SilentlyContinue | Select-Object -First 1)
    if (-not $procId) { return $false }
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    return [bool]$proc
}

# ---- 启动单个服务 ----
# Start-XzService <name> <module> <port>
function Start-XzService {
    param([string]$Name, [string]$Module, [int]$Port)

    if (Test-ServiceRunning $Name) {
        $procId = Get-Content (Get-PidFile $Name) | Select-Object -First 1
        Write-XzWarn "$Name 已在运行 (pid=$procId)"
        return
    }

    $jar = Find-Jar $Module
    if (-not $jar) {
        Write-XzErr "$Module jar 不存在，请先编译"
        return
    }

    $javaBin = Resolve-Java
    if (-not $javaBin) {
        Write-XzErr '未找到 java 可执行文件。请安装 JDK 21+ 或设置 JAVA_HOME / JAVA_BIN 环境变量'
        Write-XzErr '  例如: $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"'
        return
    }

    Write-XzInfo "启动 $Name (port $Port)..."
    Write-XzInfo "  java: $javaBin"
    if (-not (Test-Path $LogsDir)) { New-Item -ItemType Directory -Path $LogsDir | Out-Null }

    $libPath = Join-Path $RootDir 'lib'
    $outFile = Join-Path $LogsDir "$Name.out"

    # 在 $RootDir 启动，确保:
    #   1. Logback 配置中的 .\logs 写到 $RootDir\logs\
    #   2. application.yml 中 lib\, models\silero_vad.onnx 等相对路径解析正确
    $proc = Start-Process -FilePath $javaBin `
        -ArgumentList @("-Djava.library.path=$libPath", '-jar', $jar) `
        -WorkingDirectory $RootDir `
        -RedirectStandardOutput $outFile `
        -RedirectStandardError "$outFile.err" `
        -WindowStyle Hidden `
        -PassThru

    $proc.Id | Out-File -FilePath (Get-PidFile $Name) -Encoding ascii
    Write-XzOk "$Name 已启动  pid=$($proc.Id)  日志: logs\$Name.log  控制台: logs\$Name.out"
}

# ---- 停止单个服务 ----
function Stop-XzService {
    param([string]$Name)

    if (-not (Test-ServiceRunning $Name)) {
        Write-XzWarn "$Name 未在运行"
        return
    }

    $pidPath = Get-PidFile $Name
    $procId = Get-Content $pidPath | Select-Object -First 1
    Write-XzInfo "停止 $Name (pid=$procId)..."

    Stop-Process -Id $procId -ErrorAction SilentlyContinue

    # 等待最多 15 秒
    $i = 0
    while ((Get-Process -Id $procId -ErrorAction SilentlyContinue) -and ($i -lt 15)) {
        Start-Sleep -Seconds 1
        $i++
    }

    if (Get-Process -Id $procId -ErrorAction SilentlyContinue) {
        Write-XzWarn '未能正常关闭，强制结束...'
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    }

    Remove-Item $pidPath -ErrorAction SilentlyContinue
    Write-XzOk "$Name 已停止"
}

# ---- 查看状态 ----
function Get-XzServiceStatus {
    param([string]$Name, [int]$Port)
    if (Test-ServiceRunning $Name) {
        $procId = Get-Content (Get-PidFile $Name) | Select-Object -First 1
        Write-Host "  " -NoNewline
        Write-Host "+" -ForegroundColor Green -NoNewline
        Write-Host " $Name  pid=$procId  port=$Port  日志: logs\$Name.log"
    } else {
        Write-Host "  " -NoNewline
        Write-Host "o" -ForegroundColor Red -NoNewline
        Write-Host " $Name  未运行"
    }
}

# ---- 重启 ----
function Restart-XzService {
    param([string]$Name, [string]$Module, [int]$Port)
    Stop-XzService $Name
    Start-Sleep -Seconds 1
    Start-XzService $Name $Module $Port
}

# ---- 用法提示 ----
function Show-Usage {
    param([string]$Script)
    Write-Host "用法: $Script <start|stop|restart|status>"
    Write-Host "  start    编译并启动"
    Write-Host "  stop     停止"
    Write-Host "  restart  停止后重新编译并启动"
    Write-Host "  status   查看运行状态"
}
