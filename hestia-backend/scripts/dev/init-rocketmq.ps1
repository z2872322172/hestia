<#
.SYNOPSIS
    显式创建 RocketMQ Topic（演示与生产环境使用）。

.DESCRIPTION
    本地 compose 打开了 autoCreateTopicEnable，开发阶段可以省略本脚本；
    演示与生产环境必须显式创建，避免"靠自动创建"导致 Topic 参数不一致。

.NOTES
    批次 3 起创建 HK_TECH_EVENT 与订单事件 Topic HK_BOOKING_EVENT；
    派单、支付等其余 Topic 在对应批次加入。
#>
$ErrorActionPreference = 'Stop'

$nameserver = 'rocketmq-nameserver:9876'
$cluster = 'DefaultCluster'
$topics = @('HK_TECH_EVENT', 'HK_BOOKING_EVENT')

foreach ($topic in $topics) {
    Write-Host "创建 Topic $topic ..." -NoNewline
    docker exec hestia-rocketmq-broker sh mqadmin updateTopic `
        -n $nameserver -c $cluster -t $topic -r 8 -w 8 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host ' 完成' -ForegroundColor Green
    }
    else {
        Write-Warning " 失败，请确认 broker 容器名为 hestia-rocketmq-broker 且已启动。"
    }
}

Write-Host ''
Write-Host '当前 Topic 列表：' -ForegroundColor Cyan
docker exec hestia-rocketmq-broker sh mqadmin topicList -n $nameserver
