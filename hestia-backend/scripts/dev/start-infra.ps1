<#
.SYNOPSIS
    启动 Hestia 本地基础设施：MySQL、Redis、MinIO、RocketMQ（NameServer + Broker + Proxy）。

.DESCRIPTION
    1. 检查 Docker 是否可用；
    2. 执行 docker compose up -d；
    3. 轮询容器健康状态，全部就绪后输出管理端地址。

    数据卷不会被删除；需要清库请使用 reset-infra.ps1 -ConfirmReset。
#>
$ErrorActionPreference = 'Stop'

$composeFile = Resolve-Path (Join-Path $PSScriptRoot '..\..\docker\compose.yaml')
$envFile = Join-Path $PSScriptRoot '..\..\.env'
$composeArgs = @('-f', $composeFile)
if (Test-Path -LiteralPath $envFile) {
    $composeArgs = @('--env-file', (Resolve-Path $envFile), '-f', $composeFile)
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error '未检测到 docker 命令。请先安装并启动 Docker Desktop，或使用本机已安装的 MySQL/Redis 手动配置环境变量。'
    exit 1
}

Write-Host "使用 compose 文件：$composeFile" -ForegroundColor Cyan
docker compose @composeArgs up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error 'docker compose up 失败，请检查 Docker 是否已启动以及端口 3306/6379/9000/9876/8081 是否被占用。'
    exit 1
}

# 需要等待健康的容器；rocketmq 容器没有内置健康检查，这里只检查是否处于 running
$containers = @(
    @{ Name = 'hestia-mysql'; Healthy = $true },
    @{ Name = 'hestia-redis'; Healthy = $true },
    @{ Name = 'hestia-minio'; Healthy = $true },
    @{ Name = 'hestia-rocketmq-nameserver'; Healthy = $true },
    @{ Name = 'hestia-rocketmq-broker'; Healthy = $false },
    @{ Name = 'hestia-rocketmq-proxy'; Healthy = $false }
)

$timeoutSeconds = 180
$deadline = (Get-Date).AddSeconds($timeoutSeconds)

foreach ($container in $containers) {
    Write-Host "等待容器 $($container.Name) ..." -NoNewline
    while ((Get-Date) -lt $deadline) {
        $status = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $container.Name 2>$null
        if ($container.Healthy -and $status -eq 'healthy') { break }
        if (-not $container.Healthy -and $status -eq 'running') { break }
        Start-Sleep -Seconds 3
        Write-Host '.' -NoNewline
    }
    Write-Host ''
    if ($status -ne 'healthy' -and $status -ne 'running') {
        Write-Warning "容器 $($container.Name) 当前状态为 $status，请执行 docker compose logs $($container.Name) 排查。"
    }
}

Write-Host ''
Write-Host '基础设施已启动：' -ForegroundColor Green
Write-Host '  MySQL            localhost:3306   (库 hestia / hestia_test)'
Write-Host '  Redis            localhost:6379'
Write-Host '  MinIO API        http://localhost:9000'
Write-Host '  MinIO Console    http://localhost:9001  (默认 minioadmin / minioadmin)'
Write-Host '  RocketMQ NS      localhost:9876'
Write-Host '  RocketMQ Proxy   localhost:8081  (应用连接的 gRPC 端点)'
Write-Host ''
Write-Host '下一步：.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"'
