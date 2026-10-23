<#
.SYNOPSIS
    重置 Hestia 本地基础设施：删除容器与数据卷，回到空库状态。

.DESCRIPTION
    破坏性操作：会删除 MySQL、Redis、MinIO、RocketMQ 的全部本地数据。
    必须显式传入 -ConfirmReset 才会执行，避免误触。
#>
param(
    [switch]$ConfirmReset
)

$ErrorActionPreference = 'Stop'

if (-not $ConfirmReset) {
    Write-Warning '该脚本会删除 MySQL / Redis / MinIO / RocketMQ 的全部本地数据卷。'
    Write-Warning '确认要重置时请显式执行：.\reset-infra.ps1 -ConfirmReset'
    exit 1
}

$composeFile = Resolve-Path (Join-Path $PSScriptRoot '..\..\docker\compose.yaml')
$envFile = Join-Path $PSScriptRoot '..\..\.env'
$composeArgs = @('-f', $composeFile)
if (Test-Path -LiteralPath $envFile) {
    $composeArgs = @('--env-file', (Resolve-Path $envFile), '-f', $composeFile)
}

Write-Host '正在删除容器与数据卷 ...' -ForegroundColor Yellow
docker compose @composeArgs down -v
if ($LASTEXITCODE -ne 0) {
    Write-Error 'docker compose down -v 执行失败。'
    exit 1
}

Write-Host '已重置。下次启动应用时 Flyway 会从 V1 重新建表。' -ForegroundColor Green
