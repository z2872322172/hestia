<#
.SYNOPSIS
    停止 Hestia 本地基础设施容器，保留数据卷。
#>
$ErrorActionPreference = 'Stop'

$composeFile = Resolve-Path (Join-Path $PSScriptRoot '..\..\docker\compose.yaml')
$envFile = Join-Path $PSScriptRoot '..\..\.env'
$composeArgs = @('-f', $composeFile)
if (Test-Path -LiteralPath $envFile) {
    $composeArgs = @('--env-file', (Resolve-Path $envFile), '-f', $composeFile)
}

docker compose @composeArgs stop
if ($LASTEXITCODE -ne 0) {
    Write-Error 'docker compose stop 执行失败。'
    exit 1
}

Write-Host '基础设施已停止（数据卷保留）。' -ForegroundColor Green
