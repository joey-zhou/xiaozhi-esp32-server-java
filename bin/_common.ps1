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

# ---- 运行环境 ----
# Resolve-Profile [dev|prod] — 优先级：命令行参数 > 已设置的 SPRING_PROFILES_ACTIVE > dev
function Resolve-Profile {
    param([string]$Name)
    if (-not $Name) { $Name = $env:SPRING_PROFILES_ACTIVE }
    if (-not $Name) { $Name = 'dev' }
    if ($Name -notin @('dev', 'prod')) {
        throw "不支持的运行环境: $Name（只支持 dev / prod）"
    }
    return $Name
}

# ---- 端口连通性 ----
function Test-TcpPort {
    param([string]$TargetHost, [int]$Port, [int]$TimeoutMs = 3000)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($TargetHost, $Port)
        if (-not $task.Wait($TimeoutMs)) { return $false }
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

# ---- 启动前自检 ----
# 检查 JDK 版本、模型与原生库、中间件连通性，$env:SKIP_PREFLIGHT = '1' 跳过
function Invoke-Preflight {
    if ($env:SKIP_PREFLIGHT -eq '1') { return $true }

    $ok = $true

    # 1) JDK 21+
    $javaBin = Resolve-Java
    if (-not $javaBin) {
        Write-XzErr '未找到 java。请安装 JDK 21+，或设置 JAVA_HOME / JAVA_BIN'
        $ok = $false
    } else {
        $versionLine = (& $javaBin -version 2>&1 | Select-Object -First 1)
        $major = 0
        if ("$versionLine" -match '"(\d+)') { $major = [int]$Matches[1] }
        if ($major -lt 21) {
            Write-XzErr "JDK 版本过低（检测到 $major），本项目需要 21 及以上: $javaBin"
            $ok = $false
        }
    }

    # 2) 原生库与 VAD 模型
    $libDir = Join-Path $RootDir 'lib'
    if (-not (Test-Path $libDir) -or -not (Get-ChildItem $libDir -ErrorAction SilentlyContinue)) {
        Write-XzErr '缺少原生库目录 lib\，先在 Git Bash 里执行: ./scripts/download_base.sh'
        $ok = $false
    }
    if (-not (Test-Path (Join-Path $RootDir 'models\silero_vad.onnx'))) {
        Write-XzErr '缺少 VAD 模型 models\silero_vad.onnx，先在 Git Bash 里执行: ./scripts/download_base.sh'
        $ok = $false
    }
    if (-not (Test-Path (Join-Path $RootDir 'models\sense-voice'))) {
        Write-XzWarn '未检测到本地语音识别模型 models\sense-voice'
        Write-XzWarn '  用云端 STT 可以忽略；想用本地识别执行: ./scripts/download_stt.sh'
    }

    # 3) 中间件连通性
    $dbHost = 'localhost'
    $dbPort = 3306
    if ($env:SPRING_DATASOURCE_URL -match '^jdbc:mysql://([^:/?]+)(?::(\d+))?') {
        $dbHost = $Matches[1]
        if ($Matches[2]) { $dbPort = [int]$Matches[2] }
    }
    if (-not (Test-TcpPort $dbHost $dbPort)) {
        Write-XzErr "MySQL 连不上（${dbHost}:${dbPort}）"
        Write-XzErr '  没起的话执行: docker compose -f docker-compose-db.yml up -d'
        $ok = $false
    }
    $redisHost = if ($env:SPRING_DATA_REDIS_HOST) { $env:SPRING_DATA_REDIS_HOST } else { 'localhost' }
    $redisPort = if ($env:SPRING_DATA_REDIS_PORT) { [int]$env:SPRING_DATA_REDIS_PORT } else { 6379 }
    if (-not (Test-TcpPort $redisHost $redisPort)) {
        Write-XzErr "Redis 连不上（${redisHost}:${redisPort}）"
        Write-XzErr '  没起的话执行: docker compose -f docker-compose-db.yml up -d'
        $ok = $false
    }

    if (-not $ok) {
        Write-Host ''
        Write-XzErr "启动前检查未通过，按上面的提示处理后重试（确认无误可设 `$env:SKIP_PREFLIGHT = '1' 跳过）"
        return $false
    }
    Write-XzLog '启动前检查通过'
    return $true
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
# Start-XzService <name> <module> <port> [profile]
function Start-XzService {
    param([string]$Name, [string]$Module, [int]$Port, [string]$SpringProfile = 'dev')

    if (Test-ServiceRunning $Name) {
        $procId = Get-Content (Get-PidFile $Name) | Select-Object -First 1
        Write-XzWarn "$Name 已在运行 (pid=$procId)"
        return
    }

    # 未设置时设备握手鉴权关闭，本地联调可用，生产部署必须设置
    if ($null -eq (Get-Item Env:XIAOZHI_DEVICE_AUTH_SECRET -ErrorAction SilentlyContinue)) {
        Write-XzWarn "未设置 XIAOZHI_DEVICE_AUTH_SECRET，$Name 的设备握手鉴权处于关闭状态"
        Write-XzWarn '  设置方式: $env:XIAOZHI_DEVICE_AUTH_SECRET = "<32位十六进制随机串>"'
        Write-XzWarn '  server 与 dialogue 必须使用同一个值'
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

    Write-XzInfo "启动 $Name (port $Port, profile $SpringProfile)..."
    Write-XzInfo "  java: $javaBin"
    if (-not (Test-Path $LogsDir)) { New-Item -ItemType Directory -Path $LogsDir | Out-Null }

    $libPath = Join-Path $RootDir 'lib'
    $outFile = Join-Path $LogsDir "$Name.out"

    # 在 $RootDir 启动，确保:
    #   1. Logback 配置中的 .\logs 写到 $RootDir\logs\
    #   2. application.yml 中 lib\, models\silero_vad.onnx 等相对路径解析正确
    $proc = Start-Process -FilePath $javaBin `
        -ArgumentList @("-Djava.library.path=$libPath", '-jar', $jar, "--spring.profiles.active=$SpringProfile") `
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
    param([string]$Name, [string]$Module, [int]$Port, [string]$SpringProfile = 'dev')
    Stop-XzService $Name
    Start-Sleep -Seconds 1
    Start-XzService $Name $Module $Port $SpringProfile
}

# ---- 用法提示 ----
function Show-Usage {
    param([string]$Script)
    Write-Host "用法: $Script <start|stop|restart|status> [dev|prod]"
    Write-Host "  start    编译并启动"
    Write-Host "  stop     停止"
    Write-Host "  restart  停止后重新编译并启动"
    Write-Host "  status   查看运行状态"
    Write-Host '  运行环境默认 dev；可在命令后加 prod，或先设 $env:SPRING_PROFILES_ACTIVE = "prod"'
}
