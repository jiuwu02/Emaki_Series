<#
.SYNOPSIS
  经 RCON 驱动三场景（稳态 / 事件压力 / 生命周期）并采集 spark 证据（轨道 J / T15.2）。

.DESCRIPTION
  本脚本通过标准 RCON 协议（内置实现，无需外部 mcrcon 之类的工具）连接已启动的
  Paper 服务器，按场景定义发送命令，并在每个场景中采集：
    * /spark tps
    * /spark health --memory
    * /spark profiler start --timeout <N> 与 /spark profiler stop --save-to-file
  以及从 GC 日志中截取该场景时间窗内的增量（若提供 -GcLogPath）。

  所有原始输出按场景存到带时间戳的结果目录，供 parse_report.py 解析与前后对比：
    <OutRoot>\<Label>-<yyyyMMdd-HHmmss>\
      run-meta.txt          本轮参数（不含密码）
      <scenario>\commands.log   该场景全部命令与原始回包
      <scenario>\tps.txt        /spark tps 原始输出
      <scenario>\health.txt     /spark health --memory 原始输出
      <scenario>\profiler.txt   /spark profiler start|stop 原始输出
      <scenario>\gc.log         该场景时间窗内的 GC 日志增量（若可用）

  脚本可重复运行：结果目录带时间戳，不覆盖、不删除任何已有文件。

.PARAMETER RconHost
  RCON 主机。缺省 127.0.0.1。

.PARAMETER Port
  RCON 端口。缺省 25575（对应 server.properties 的 rcon.port）。

.PARAMETER Password
  RCON 密码（对应 server.properties 的 rcon.password）。未提供时交互式读取。

.PARAMETER OutRoot
  结果根目录。缺省 <脚本目录>\results。

.PARAMETER Label
  结果目录标签，用于区分 before / after。缺省 run。

.PARAMETER SparkTimeout
  /spark profiler start 的 --timeout 秒数。缺省 120。

.PARAMETER Only
  仅运行指定场景（键名：steady / pressure / lifecycle）。缺省全部按序运行。

.PARAMETER MobId
  替换场景命令中 <mob_id> 占位符的受管生物 id（默认示例值，需按实机配置改）。

.PARAMETER GcLogPath
  GC 日志文件绝对/相对路径（即 run-server.ps1 的 -GcLog，默认位于服务器目录）。
  提供后按场景截取增量写入 <scenario>\gc.log。

.PARAMETER SkipProfiler
  跳过 /spark profiler start|stop，仅采 TPS 与 health。

.PARAMETER DryRun
  只打印将要发送的命令，不建立 RCON 连接（用于离线核对场景配置）。

.EXAMPLE
  .\collect.ps1 -Password '你的rcon密码' -Label before -GcLogPath C:\srv\paper\gc.log
  .\collect.ps1 -Password '你的rcon密码' -Label after -Only steady,pressure

.NOTES
  前置条件：
    * 服务器已用 run-server.ps1 启动，且 server.properties 中已开启：
      enable-rcon=true / rcon.port=<Port> / rcon.password=<Password>；
    * 服务器已安装 spark 插件（否则 /spark 命令无响应）。
  若无法连接 RCON，脚本会以非零退出码报错，并提示上述前置条件。
#>
[CmdletBinding()]
param(
    [string]$RconHost = '127.0.0.1',
    [int]$Port = 25575,
    [string]$Password,
    [string]$OutRoot,
    [string]$Label = 'run',
    [int]$SparkTimeout = 120,
    [string[]]$Only = @(),
    [string]$MobId = 'example_mob',
    [string]$GcLogPath,
    [switch]$SkipProfiler,
    [switch]$DryRun,
    [int]$ConnectTimeoutMs = 5000,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$script:DryRun = [bool]$DryRun

function Show-Usage {
    Write-Host @'
collect.ps1 —— 经 RCON 驱动三场景并采集 spark 证据

用法：
  .\collect.ps1 -Password <rcon密码> [-RconHost 127.0.0.1] [-Port 25575]
                [-OutRoot <dir>] [-Label before|after] [-SparkTimeout 120]
                [-Only steady,pressure,lifecycle] [-MobId <mob_id>]
                [-GcLogPath <gc.log>] [-SkipProfiler] [-DryRun] [-Help]

场景命令行内联在本脚本顶部的 $Scenarios 配置块，可直接编辑以贴合实机。
'@
}

if ($Help) { Show-Usage; exit 0 }

# ============================================================================
#  场景命令配置（用户可改）
#  ---------------------------------------------------------------------------
#  每个场景支持字段：
#    Title              : 展示名
#    Setup              : 采样前执行一次的条目数组
#    During             : 采样窗口内按 LoopIntervalSeconds 周期重复执行的条目数组
#    Teardown           : 采样后执行一次的条目数组
#    DurationSeconds    : 采样窗口时长（秒）
#    LoopIntervalSeconds: During 条目的重复间隔（秒）
#    RunProfiler        : 是否采集 /spark profiler
#    StopServer         : 场景结束后是否发送 /stop 停服（仅生命周期场景）
#  条目既可为字符串（单条命令），也可为哈希表：
#    @{ Cmd='...'; Repeat=500; IntervalMs=20 }   # Repeat 次，每次间隔 IntervalMs 毫秒
#  命令中的 <mob_id> 会被 -MobId 替换。
#  注意：示例命令中的选择器/数值仅为占位，请按实机世界与配置调整。
# ============================================================================
$Scenarios = [ordered]@{
    'steady' = @{
        Title               = '稳态'
        # 无操作 5 分钟，仅自然刷怪与玩家自然回血
        Setup               = @()
        During              = @()
        Teardown            = @()
        DurationSeconds     = 300
        LoopIntervalSeconds = 30
        RunProfiler         = $true
        StopServer          = $false
    }
    'pressure' = @{
        Title               = '事件压力'
        # 示例：集中刷出 500 个受管生物（<mob_id> 请替换为实机存在的生物 id）
        Setup               = @(
            @{ Cmd = '/emakimobs spawn <mob_id>'; Repeat = 500; IntervalMs = 20 }
        )
        # 示例：让全体玩家周期性对附近非玩家实体造成伤害。
        # 「20 名玩家」需先行真实进入（或用机器人插件），本脚本只负责发命令。
        During              = @(
            @{ Cmd = '/execute as @a at @s run damage @e[distance=..8,type=!player] 4' }
        )
        Teardown            = @(
            '/emakimobs count'
        )
        DurationSeconds     = 180
        LoopIntervalSeconds = 5
        RunProfiler         = $true
        StopServer          = $false
    }
    'lifecycle' = @{
        Title               = '生命周期'
        # 采集窗口内保持静止，结束后 reload 并停服，验证无泄漏/无残留任务/停服不超时
        Setup               = @()
        During              = @()
        Teardown            = @(
            '/emakimobs reload',
            '/emakimobs count'
        )
        DurationSeconds     = 60
        LoopIntervalSeconds = 30
        RunProfiler         = $false
        StopServer          = $true
    }
}

# ============================================================================
#  RCON 协议实现（TCP；little-endian：length + id + type + body + 0x00 0x00）
# ============================================================================
function Read-Exact {
    param([System.IO.Stream]$Stream, [int]$Count)
    $buf = New-Object byte[] $Count
    $off = 0
    while ($off -lt $Count) {
        $n = $Stream.Read($buf, $off, $Count - $off)
        if ($n -le 0) { throw 'RCON 连接已被服务器关闭。' }
        $off += $n
    }
    return $buf
}

function Read-RconPacket {
    param([System.IO.Stream]$Stream)
    $lenBytes = Read-Exact -Stream $Stream -Count 4
    $len = [System.BitConverter]::ToInt32($lenBytes, 0)
    if ($len -lt 10 -or $len -gt 1048576) { throw "RCON 响应长度异常：$len（协议不匹配？）" }
    $payload = Read-Exact -Stream $Stream -Count $len
    $id = [System.BitConverter]::ToInt32($payload, 0)
    $type = [System.BitConverter]::ToInt32($payload, 4)
    $bodyLen = $len - 10
    $body = if ($bodyLen -gt 0) { [System.Text.Encoding]::UTF8.GetString($payload, 8, $bodyLen) } else { '' }
    return [pscustomobject]@{ Id = $id; Type = $type; Body = $body }
}

function Write-RconPacket {
    param([System.IO.Stream]$Stream, [int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $len = 4 + 4 + $bodyBytes.Length + 2
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    try {
        $bw.Write([int]$len)
        $bw.Write([int]$Id)
        $bw.Write([int]$Type)
        $bw.Write($bodyBytes)
        $bw.Write([byte]0)
        $bw.Write([byte]0)
        $bw.Flush()
        $bytes = $ms.ToArray()
    } finally {
        $bw.Dispose()
        $ms.Dispose()
    }
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()
}

function Connect-Rcon {
    param([string]$RHost, [int]$RPort, [string]$RPassword, [int]$TimeoutMs)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($RHost, $RPort)
        if (-not $task.Wait($TimeoutMs)) { throw "连接 $RHost`:$RPort 超时（${TimeoutMs}ms）。" }
        if ($task.IsFaulted) { throw $task.Exception.GetBaseException().Message }
    } catch {
        $client.Close()
        throw
    }
    $client.ReceiveTimeout = 3000
    $client.SendTimeout = 3000
    $stream = $client.GetStream()

    Write-RconPacket -Stream $stream -Id 1 -Type 3 -Body $RPassword
    $resp = Read-RconPacket -Stream $stream
    if ($resp.Id -eq -1) {
        $client.Close()
        throw 'RCON 认证失败：密码错误（请核对 server.properties 的 rcon.password）。'
    }
    return [pscustomobject]@{ Client = $client; Stream = $stream; NextId = 2 }
}

function Invoke-RconCommand {
    param(
        $Conn,
        [Parameter(Mandatory = $true)][string]$Command,
        [switch]$AllowNoResponse
    )
    if ($script:DryRun) {
        Write-Host "    [dry-run] > $Command" -ForegroundColor DarkGray
        return ''
    }
    Write-RconPacket -Stream $Conn.Stream -Id $Conn.NextId -Type 2 -Body $Command
    $Conn.NextId++

    $parts = New-Object System.Collections.Generic.List[string]
    $first = $true
    while ($true) {
        try {
            $pkt = Read-RconPacket -Stream $Conn.Stream
        } catch {
            # /stop 之类命令服务器可能直接断开，视为正常
            if ($first -and $AllowNoResponse) { return '' }
            throw
        }
        $first = $false
        if ($pkt.Body) { $parts.Add($pkt.Body) }
        # 响应可能被拆成多个 RCON 包：短暂等待确认是否还有后续分片
        if (-not $Conn.Stream.DataAvailable) {
            Start-Sleep -Milliseconds 120
            if (-not $Conn.Stream.DataAvailable) { break }
        }
    }
    return ($parts -join "`n")
}

# ============================================================================
#  辅助函数
# ============================================================================
function Resolve-CommandTemplate {
    param([string]$Cmd)
    return $Cmd.Replace('<mob_id>', $MobId)
}

function Invoke-ScenarioEntry {
    param($Conn, $Entry, [System.Collections.Generic.List[string]]$Log, [string]$LogPath)
    if ($Entry -is [string]) {
        $items = @([pscustomobject]@{ Cmd = $Entry; Repeat = 1; IntervalMs = 0 })
    } else {
        $repeat = if ($Entry.ContainsKey('Repeat')) { [int]$Entry.Repeat } else { 1 }
        $interval = if ($Entry.ContainsKey('IntervalMs')) { [int]$Entry.IntervalMs } else { 0 }
        $items = @([pscustomobject]@{ Cmd = $Entry.Cmd; Repeat = $repeat; IntervalMs = $interval })
    }
    foreach ($it in $items) {
        $cmd = Resolve-CommandTemplate -Cmd $it.Cmd
        # 试运行时限制重复次数，避免刷屏
        $repeat = if ($script:DryRun) { [Math]::Min($it.Repeat, 3) } else { $it.Repeat }
        if ($script:DryRun -and $it.Repeat -gt $repeat) {
            Write-Host "    [dry-run] （Repeat=$($it.Repeat)，仅打印前 $repeat 次）" -ForegroundColor DarkGray
        }
        for ($i = 0; $i -lt $repeat; $i++) {
            $out = Invoke-RconCommand -Conn $Conn -Command $cmd
            $Log.Add("> $cmd")
            if ($out) { $Log.Add($out) }
            if (-not $script:DryRun -and $it.IntervalMs -gt 0 -and $i -lt ($repeat - 1)) {
                Start-Sleep -Milliseconds $it.IntervalMs
            }
        }
    }
    if (-not $script:DryRun) { Save-Log -Log $Log -Path $LogPath }
}

function Save-Log {
    param([System.Collections.Generic.List[string]]$Log, [string]$Path)
    Set-Content -LiteralPath $Path -Value ($Log -join [Environment]::NewLine) -Encoding UTF8
}

# 读取 GC 日志当前长度（作为场景增量起点）
function Get-GcLength {
    if ($GcLogPath -and (Test-Path -LiteralPath $GcLogPath)) {
        try { return (Get-Item -LiteralPath $GcLogPath).Length } catch { return 0 }
    }
    return 0
}

# 截取 GC 日志 [startLen, 当前] 的增量写入场景目录；返回是否成功
function Save-GcDelta {
    param([string]$Dir, [long]$StartLen)
    if (-not $GcLogPath -or -not (Test-Path -LiteralPath $GcLogPath)) { return $false }
    try {
        $fs = [System.IO.File]::Open($GcLogPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $cur = $fs.Length
            $offset = if ($cur -lt $StartLen) { 0 } else { $StartLen }   # 日志被轮转则退回整文件
            $fs.Seek($offset, [System.IO.SeekOrigin]::Begin) | Out-Null
            $sr = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::UTF8)
            $content = $sr.ReadToEnd()
        } finally {
            $fs.Dispose()
        }
        Set-Content -LiteralPath (Join-Path $Dir 'gc.log') -Value $content -Encoding UTF8
        return $true
    } catch {
        Write-Host "[WARN] 截取 GC 日志增量失败：$($_.Exception.Message)" -ForegroundColor Yellow
        return $false
    }
}

# ============================================================================
#  主流程
# ============================================================================
if (-not $script:DryRun) {
    if (-not $Password) {
        $sec = Read-Host -Prompt '请输入 RCON 密码' -AsSecureString
        $Password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
    }
    if (-not $Password) {
        Write-Host '[错误] RCON 密码为空。' -ForegroundColor Red
        exit 2
    }
}

if (-not $OutRoot) { $OutRoot = Join-Path $PSScriptRoot 'results' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $OutRoot "$Label-$stamp"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$selected = if ($Only.Count -gt 0) { $Only } else { @($Scenarios.Keys) }
foreach ($key in $selected) {
    if (-not $Scenarios.Contains($key)) {
        Write-Host "[错误] 未知场景键：$key（可用：$($Scenarios.Keys -join ', ')）" -ForegroundColor Red
        exit 2
    }
}

Write-Host '------------------------------------------------------------'
Write-Host '  Emaki 性能采集（RCON）'
Write-Host '------------------------------------------------------------'
Write-Host "  目标      : $RconHost`:$Port"
Write-Host "  场景      : $($selected -join ', ')"
Write-Host "  结果目录  : $outDir"
Write-Host "  GC 日志   : $(if ($GcLogPath) { $GcLogPath } else { '(未提供)' })"
Write-Host "  试运行    : $([bool]$script:DryRun)"
Write-Host '------------------------------------------------------------'

# 记录本轮参数（绝不含密码）
$meta = @(
    "label=$Label",
    "time=$stamp",
    "host=$RconHost",
    "port=$Port",
    "scenarios=$($selected -join ',')",
    "spark_timeout=$SparkTimeout",
    "mob_id=$MobId",
    "profiler=$(-not $SkipProfiler)",
    "gc_log=$GcLogPath"
)
Set-Content -LiteralPath (Join-Path $outDir 'run-meta.txt') -Value ($meta -join [Environment]::NewLine) -Encoding UTF8

$conn = $null
if (-not $script:DryRun) {
    try {
        $conn = Connect-Rcon -RHost $RconHost -RPort $Port -RPassword $Password -TimeoutMs $ConnectTimeoutMs
        Write-Host '[信息] RCON 已连接。' -ForegroundColor Green
    } catch {
        Write-Host "[错误] 无法连接 RCON：$($_.Exception.Message)" -ForegroundColor Red
        Write-Host '       请确认服务器已启动，且 server.properties 中：' -ForegroundColor Red
        Write-Host '         enable-rcon=true' -ForegroundColor Red
        Write-Host "         rcon.port=$Port" -ForegroundColor Red
        Write-Host '         rcon.password=<与 -Password 一致>' -ForegroundColor Red
        exit 1
    }
}

try {
    foreach ($key in $selected) {
        $scn = $Scenarios[$key]
        $dir = Join-Path $outDir $key
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host ''
        Write-Host "==== 场景 $key（$($scn.Title)）====" -ForegroundColor Cyan

        $log = New-Object System.Collections.Generic.List[string]
        $log.Add("# 场景 $key ($($scn.Title)) 开始于 $(Get-Date -Format o)")
        $logPath = Join-Path $dir 'commands.log'

        $gcStart = Get-GcLength

        # 1) 场景前置
        foreach ($e in $scn.Setup) { Invoke-ScenarioEntry -Conn $conn -Entry $e -Log $log -LogPath $logPath }

        # 2) 启动 profiler（输出与停止结果汇总到同一份 profiler.txt）
        $profLog = $null
        if ($scn.RunProfiler -and -not $SkipProfiler) {
            $profLog = New-Object System.Collections.Generic.List[string]
            $profLog.Add("> /spark profiler start --timeout $SparkTimeout")
            $profLog.Add((Invoke-RconCommand -Conn $conn -Command "/spark profiler start --timeout $SparkTimeout" -AllowNoResponse))
        }

        # 3) 采样窗口：按间隔重复 During 条目（试运行时各发一次、不等待）
        if ($script:DryRun) {
            foreach ($e in $scn.During) { Invoke-ScenarioEntry -Conn $conn -Entry $e -Log $log -LogPath $logPath }
        } else {
            $deadline = (Get-Date).AddSeconds([int]$scn.DurationSeconds)
            while ((Get-Date) -lt $deadline) {
                foreach ($e in $scn.During) { Invoke-ScenarioEntry -Conn $conn -Entry $e -Log $log -LogPath $logPath }
                if ((Get-Date) -lt $deadline) { Start-Sleep -Seconds ([int]$scn.LoopIntervalSeconds) }
            }
        }

        # 4) 采 TPS 与 health（在窗口末期，反映负载稳定后的状态）
        $tpsOut = Invoke-RconCommand -Conn $conn -Command '/spark tps' -AllowNoResponse
        $log.Add('> /spark tps'); $log.Add($tpsOut)
        $healthOut = Invoke-RconCommand -Conn $conn -Command '/spark health --memory' -AllowNoResponse
        $log.Add('> /spark health --memory'); $log.Add($healthOut)
        if (-not $script:DryRun) {
            Set-Content -LiteralPath (Join-Path $dir 'tps.txt') -Value $tpsOut -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $dir 'health.txt') -Value $healthOut -Encoding UTF8
        }

        # 5) 停止 profiler 并存盘
        if ($profLog) {
            $profStop = Invoke-RconCommand -Conn $conn -Command '/spark profiler stop --save-to-file' -AllowNoResponse
            $profLog.Add('> /spark profiler stop --save-to-file')
            $profLog.Add($profStop)
            if (-not $script:DryRun) {
                Set-Content -LiteralPath (Join-Path $dir 'profiler.txt') -Value ($profLog -join [Environment]::NewLine) -Encoding UTF8
            }
        }

        # 6) 场景后置
        foreach ($e in $scn.Teardown) { Invoke-ScenarioEntry -Conn $conn -Entry $e -Log $log -LogPath $logPath }

        # 7) 截取 GC 增量
        if (-not $script:DryRun) {
            if (Save-GcDelta -Dir $dir -StartLen $gcStart) {
                $log.Add("# GC 日志增量已写入 gc.log")
            } elseif ($GcLogPath) {
                $log.Add("# [WARN] 未能截取 GC 日志增量（请检查 -GcLogPath）")
            }
        }

        $log.Add("# 场景 $key 结束于 $(Get-Date -Format o)")
        Save-Log -Log $log -Path $logPath

        # 8) 生命周期场景：停服（连接会断开，属预期）
        if ($scn.StopServer) {
            Write-Host '[信息] 发送 /stop 停服（连接随后断开属预期）。' -ForegroundColor Yellow
            Invoke-RconCommand -Conn $conn -Command '/stop' -AllowNoResponse | Out-Null
            break
        }
    }
} finally {
    if ($conn) {
        try { $conn.Stream.Close(); $conn.Client.Close() } catch { }
    }
}

Write-Host ''
Write-Host "[完成] 结果已保存到：$outDir" -ForegroundColor Green
Write-Host '        下一步：python parse_report.py --dir "<上述目录>"（单轮汇总）' -ForegroundColor Green
Write-Host '                 python parse_report.py --before <before目录> --after <after目录>（前后对比）' -ForegroundColor Green
