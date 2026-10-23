<#
.SYNOPSIS
    技术底座冒烟验证：健康检查、Swagger、地图、事件链路、文件上传。

.DESCRIPTION
    前提：应用已以 local profile 启动，且 docker compose 基础设施全部健康。

.PARAMETER BaseUrl
    应用地址，默认 http://localhost:8080

.EXAMPLE
    .\smoke-test.ps1
#>
param(
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$failed = 0

function Invoke-Check {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    Write-Host "[检查] $Name ..." -NoNewline
    try {
        & $Action
        Write-Host ' 通过' -ForegroundColor Green
    }
    catch {
        $script:failed++
        Write-Host " 失败：$($_.Exception.Message)" -ForegroundColor Red
    }
}

Invoke-Check '应用心跳 /api/v1/system/ping' {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/system/ping" -TimeoutSec 10
    if ($response.code -ne 'OK') { throw "code=$($response.code)" }
    Write-Host " profile=$($response.data.profile)" -NoNewline
}

Invoke-Check '集成状态 /api/v1/system/integrations' {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/system/integrations" -TimeoutSec 30
    $response.data.components.PSObject.Properties | ForEach-Object {
        Write-Host " $($_.Name)=$($_.Value)" -NoNewline
    }
}

Invoke-Check '健康检查 /actuator/health' {
    $response = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 30
    Write-Host " status=$($response.status)" -NoNewline
    if ($response.status -ne 'UP') { throw "健康状态为 $($response.status)" }
}

Invoke-Check 'OpenAPI 文档 /v3/api-docs' {
    $response = Invoke-RestMethod -Uri "$BaseUrl/v3/api-docs" -TimeoutSec 15
    if (-not $response.paths) { throw '文档未返回任何接口' }
}

Invoke-Check '地理编码 /api/v1/system/smoke/geo' {
    $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/system/smoke/geo?address=%E6%A1%82%E6%9E%97%E5%B8%82%E8%B1%A1%E5%B1%B1%E5%8C%BA" -TimeoutSec 15
    Write-Host " provider=$($response.data.provider) lng=$($response.data.longitude) lat=$($response.data.latitude)" -NoNewline
}

Invoke-Check '事件链路 SCAFFOLD_PING' {
    $created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/system/smoke/event" -TimeoutSec 15
    $eventId = $created.data.eventId
    Write-Host " eventId=$eventId" -NoNewline

    # Relay 默认 2 秒一轮，最多等 30 秒
    $consumed = $false
    for ($i = 0; $i -lt 15; $i++) {
        Start-Sleep -Seconds 2
        $status = Invoke-RestMethod -Uri "$BaseUrl/api/v1/system/smoke/event/$eventId" -TimeoutSec 15
        if ($status.data.consumed) {
            $consumed = $true
            Write-Host " outbox=$($status.data.outboxStatus) consumed=true" -NoNewline
            break
        }
    }
    if (-not $consumed) { throw '30 秒内未观察到消费成功，请检查 RocketMQ Proxy 与消费者日志' }
}

Invoke-Check '文件上传 /api/v1/system/smoke/file' {
    # 1x1 PNG，用于验证文件头校验与预签名地址
    $pngBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/AL+2wUAAAAASUVORK5CYII=')
    $tempFile = Join-Path $env:TEMP 'hestia-smoke.png'
    [IO.File]::WriteAllBytes($tempFile, $pngBytes)

    $form = @{ file = Get-Item $tempFile }
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/system/smoke/file" -Form $form -TimeoutSec 30
    Write-Host " objectKey=$($response.data.objectKey)" -NoNewline
    if (-not $response.data.url) { throw '未返回预签名地址' }
    Remove-Item $tempFile -ErrorAction SilentlyContinue
}

Write-Host ''
if ($failed -eq 0) {
    Write-Host '冒烟验证全部通过。' -ForegroundColor Green
    exit 0
}
Write-Warning "共有 $failed 项检查失败。"
exit 1
