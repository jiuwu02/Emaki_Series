<#
.SYNOPSIS
  启动一台用于性能测量的 Paper 服务器（轨道 J / T15.1）。

.DESCRIPTION
  本脚本只负责“把服务器按可测量的姿势拉起来”，不做任何采集与解析：
    * 校验 java 可执行文件与服务器 jar 是否存在，缺失即报错退出；
    * 需要时把已构建的模块 jar 复制进 plugins/ 目录；
    * 注入 JMX 参数（VisualVM / JProfiler 远程附加）；
    * 开启统一 GC 日志（-Xlog:gc*,gc+heap=info:file=gc.log:...）；
    * 检查 plugins/ 下是否存在 spark 插件，缺失时给出下载提示（不自动下载）。

  本脚本不会下载任何东西、不修改任何插件源码、不删除任何用户数据；重复运行安全
  （模块 jar 为覆盖复制，服务器目录与已有 plugins 内容保持不变）。

.PARAMETER ServerJar
  Paper 服务端 jar 路径（必填）。例如 C:\srv\paper\paper-1.21.8.jar。

.PARAMETER ServerDir
  服务器工作目录（server.properties / plugins / worlds 所在）。缺省取 ServerJar 所在目录。

.PARAMETER PluginsDir
  plugins 目录。缺省取 <ServerDir>\plugins；不存在时会创建（空目录，不含用户数据）。

.PARAMETER ModuleJars
  要复制进 plugins/ 的已构建模块 jar 列表，例如
  ..\..\EmakiCoreLib\target\EmakiCoreLib-4.8.18.jar。可传多个。

.PARAMETER Memory
  堆大小，同时用于 -Xms 与 -Xmx。缺省 4G。

.PARAMETER JmxPort
  JMX 远程端口。缺省 9010。

.PARAMETER JavaExe
  java 可执行文件（名称或绝对路径）。缺省 java（从 PATH 解析）。

.PARAMETER GcLog
  GC 日志文件名（相对 ServerDir）。缺省 gc.log。

.PARAMETER NoJmx
  加此开关则不加 JMX 参数。

.PARAMETER NoGcLog
  加此开关则不开启 GC 日志。

.PARAMETER ExtraJvmArgs
  追加的额外 JVM 参数（原样透传，例如 '-XX:+UseG1GC'）。

.EXAMPLE
  # 启动并把四个已构建模块 jar 复制进 plugins/，开启 JMX(9010) 与 GC 日志
  .\run-server.ps1 -ServerJar C:\srv\paper\paper.jar `
      -ModuleJars ..\..\EmakiCoreLib\target\EmakiCoreLib-4.8.18.jar, `
                   ..\..\EmakiMobs\target\EmakiMobs-1.0.6.jar

.EXAMPLE
  # 只看用法
  .\run-server.ps1 -Help

.NOTES
  前置条件：本机已安装 JDK（java 可在 PATH 中或经 -JavaExe 指定）；已放置 Paper 服务端 jar；
  建议安装 spark 插件用于 TPS/MSPT 采样（脚本不会自动下载）。
  安全提示：JMX 以 authenticate=false / ssl=false 开放，仅应在可信网络（本机/内网）短期使用，
  测量结束后请关闭服务器，切勿把该端口暴露到公网。
#>
[CmdletBinding()]
param(
    [string]$ServerJar,
    [string]$ServerDir,
    [string]$PluginsDir,
    [string[]]$ModuleJars = @(),
    [string]$Memory = '4G',
    [int]$JmxPort = 9010,
    [string]$JavaExe = 'java',
    [string]$GcLog = 'gc.log',
    [switch]$NoJmx,
    [switch]$NoGcLog,
    [string[]]$ExtraJvmArgs = @(),
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

# spark 插件下载提示（脚本不执行下载，仅提示）
$SparkHint = 'https://www.spigotmc.org/resources/spark.57242/ （或 https://ci.lucko.me/job/spark）'

function Show-Usage {
    Write-Host @'
run-server.ps1 —— 启动用于性能测量的 Paper 服务器

用法：
  .\run-server.ps1 -ServerJar <paper.jar> [-ServerDir <dir>] [-PluginsDir <dir>]
                   [-ModuleJars <jar...>] [-Memory 4G] [-JmxPort 9010]
                   [-JavaExe java] [-GcLog gc.log] [-NoJmx] [-NoGcLog]
                   [-ExtraJvmArgs <arg...>] [-Help]

说明：
  * 注入 JMX（VisualVM/JProfiler 附加）：-Dcom.sun.management.jmxremote
    -Dcom.sun.management.jmxremote.port=<JmxPort>
    -Dcom.sun.management.jmxremote.authenticate=false
    -Dcom.sun.management.jmxremote.ssl=false
  * 注入 GC 日志：-Xlog:gc*,gc+heap=info:file=<GcLog>:time,uptime,level,tags
  * 若 plugins/ 下无 spark 插件，仅打印 WARN 与下载提示，不自动下载。
'@
}

if ($Help) { Show-Usage; exit 0 }

if (-not $ServerJar) {
    Write-Host '[错误] 缺少 -ServerJar（Paper 服务端 jar 路径）。使用 -Help 查看用法。' -ForegroundColor Red
    exit 2
}

# --- 1. 校验 java 可执行文件 ---
$javaCmd = Get-Command $JavaExe -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host "[错误] 找不到 java 可执行文件：$JavaExe" -ForegroundColor Red
    Write-Host '        请安装 JDK，或经 -JavaExe 指定 java 的绝对路径。' -ForegroundColor Red
    exit 1
}
$javaPath = $javaCmd.Source
if (-not (Test-Path -LiteralPath $javaPath)) {
    Write-Host "[错误] java 路径不可访问：$javaPath" -ForegroundColor Red
    exit 1
}
# 轻量探活：确认 java 真的能运行。
# 注意：java -version 向 stderr 输出，而本脚本 $ErrorActionPreference=Stop 会把
# 原生 stderr 视为终止错误，故此处临时切到 Continue 并合并流。
$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$null = & $javaPath -version 2>&1
$javaExit = $LASTEXITCODE
$ErrorActionPreference = $prevEap
if ($javaExit -ne 0) {
    Write-Host "[错误] java 无法运行（退出码 $javaExit）：$javaPath" -ForegroundColor Red
    exit 1
}

# --- 2. 校验服务器 jar 与目录 ---
if (-not (Test-Path -LiteralPath $ServerJar -PathType Leaf)) {
    Write-Host "[错误] 找不到服务器 jar：$ServerJar" -ForegroundColor Red
    exit 1
}
$ServerJar = (Resolve-Path -LiteralPath $ServerJar).Path
if (-not $ServerDir) { $ServerDir = Split-Path -Parent $ServerJar }
if (-not (Test-Path -LiteralPath $ServerDir -PathType Container)) {
    Write-Host "[错误] 服务器目录不存在：$ServerDir" -ForegroundColor Red
    exit 1
}
$ServerDir = (Resolve-Path -LiteralPath $ServerDir).Path

if (-not $PluginsDir) { $PluginsDir = Join-Path $ServerDir 'plugins' }
if (-not (Test-Path -LiteralPath $PluginsDir)) {
    # 仅创建空目录，不触碰任何已存在内容
    New-Item -ItemType Directory -Path $PluginsDir -Force | Out-Null
    Write-Host "[信息] 已创建 plugins 目录：$PluginsDir"
}
$PluginsDir = (Resolve-Path -LiteralPath $PluginsDir).Path

# --- 3. 复制已构建模块 jar（覆盖复制，可重复运行） ---
foreach ($jar in $ModuleJars) {
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        Write-Host "[错误] 模块 jar 不存在：$jar" -ForegroundColor Red
        exit 1
    }
    Copy-Item -LiteralPath $jar -Destination $PluginsDir -Force
    Write-Host "[信息] 已复制 $(Split-Path -Leaf $jar) -> $PluginsDir"
}

# --- 4. spark 插件检查（缺失仅告警，不下载） ---
$spark = Get-ChildItem -LiteralPath $PluginsDir -Filter 'spark*.jar' -File -ErrorAction SilentlyContinue
if (-not $spark) {
    Write-Host '[WARN] plugins/ 下未发现 spark 插件；本套采集脚本依赖 /spark tps|health|profiler。' -ForegroundColor Yellow
    Write-Host "[WARN] 请手动下载 spark 并放入 plugins/：$SparkHint" -ForegroundColor Yellow
} else {
    Write-Host "[信息] 检测到 spark 插件：$($spark[0].Name)"
}

# --- 5. 组装 JVM 参数 ---
$jvm = @("-Xms$Memory", "-Xmx$Memory")

if (-not $NoJmx) {
    $jvm += '-Dcom.sun.management.jmxremote'
    $jvm += "-Dcom.sun.management.jmxremote.port=$JmxPort"
    $jvm += '-Dcom.sun.management.jmxremote.authenticate=false'
    $jvm += '-Dcom.sun.management.jmxremote.ssl=false'
} else {
    Write-Host '[信息] 已按 -NoJmx 跳过 JMX 参数。'
}

if (-not $NoGcLog) {
    # 必须用 ${GcLog}：紧邻的 ':' 会被 PowerShell 当作作用域分隔符而吞掉变量名
    $jvm += "-Xlog:gc*,gc+heap=info:file=${GcLog}:time,uptime,level,tags"
} else {
    Write-Host '[信息] 已按 -NoGcLog 跳过 GC 日志参数。'
}

if ($ExtraJvmArgs.Count -gt 0) { $jvm += $ExtraJvmArgs }

$gcLogAbs = if ($NoGcLog) { '(未启用)' } elseif ([System.IO.Path]::IsPathRooted($GcLog)) { $GcLog } else { Join-Path $ServerDir $GcLog }

# --- 6. 打印摘要并启动 ---
Write-Host '------------------------------------------------------------'
Write-Host '  Emaki 性能测量服务器启动'
Write-Host '------------------------------------------------------------'
Write-Host "  java       : $javaPath"
Write-Host "  服务器 jar : $ServerJar"
Write-Host "  工作目录   : $ServerDir"
Write-Host "  plugins    : $PluginsDir"
Write-Host "  堆内存     : $Memory"
Write-Host "  JMX 端口   : $(if ($NoJmx) { '已禁用' } else { $JmxPort })"
Write-Host "  GC 日志    : $gcLogAbs"
Write-Host "  JVM 参数   : $($jvm -join ' ')"
if (-not $NoJmx) {
    Write-Host "  附加方式   : VisualVM -> 远程 -> $([System.Net.Dns]::GetHostName()):$JmxPort（或 jconsole <host>:$JmxPort）"
}
Write-Host '------------------------------------------------------------'

$serverArgs = @('-jar', $ServerJar, '--nogui')
$allArgs = $jvm + $serverArgs

# 必须在服务器目录内启动，服务器才会读写到正确的 worlds/plugins/server.properties。
# 启动服务器时切到 Continue：服务端日志可能写入 stderr，Stop 会把它当成终止错误而中断。
$ErrorActionPreference = 'Continue'
Push-Location -LiteralPath $ServerDir
try {
    & $javaPath @allArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
