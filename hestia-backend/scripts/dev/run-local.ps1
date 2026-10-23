<#
.SYNOPSIS
    读取项目根目录的 .env，注入环境变量后用 local profile 启动后端。

.DESCRIPTION
    Spring Boot 不识别 .env 文件，本脚本把 KEY=VALUE 逐行写入当前进程环境变量，
    再启动已打包的可执行 jar；找不到 jar 时回退到 mvnw spring-boot:run。

.PARAMETER Profile
    Spring profile，默认 local。

.PARAMETER Rebuild
    忽略已有 jar，强制走 mvnw spring-boot:run（需要 Maven 可用）。

.EXAMPLE
    .\scripts\dev\run-local.ps1
    .\scripts\dev\run-local.ps1 -Profile demo
#>
param(
    [string]$Profile = 'local',
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$envFile = Join-Path $root '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "缺少 $envFile。请先复制 .env.example 为 .env，并按本机基础设施填写连接信息。"
}

# 只做 KEY=VALUE 解析：忽略空行与注释；值两侧的引号去掉，避免把引号带进 JDBC URL
$count = 0
foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    $text = $line.Trim()
    if (-not $text -or $text.StartsWith('#')) { continue }
    $index = $text.IndexOf('=')
    if ($index -le 0) { continue }
    $key = $text.Substring(0, $index).Trim()
    $value = $text.Substring($index + 1).Trim().Trim('"').Trim("'")
    Set-Item -Path "Env:$key" -Value $value
    $count++
}
Write-Host "已从 .env 注入 $count 个环境变量" -ForegroundColor Cyan

if (-not $Rebuild) {
    $jar = Get-ChildItem -Path (Join-Path $root 'target\housekeeping-platform-*.jar') -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.original' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($jar) {
        Write-Host "启动 $($jar.Name)（profile=$Profile）" -ForegroundColor Green
        & java -jar $jar.FullName "--spring.profiles.active=$Profile"
        exit $LASTEXITCODE
    }
}

Write-Host '未找到可执行 jar，回退到 mvnw spring-boot:run' -ForegroundColor Yellow
& (Join-Path $root 'mvnw.cmd') spring-boot:run "-Dspring-boot.run.profiles=$Profile"
exit $LASTEXITCODE
