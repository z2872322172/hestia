@echo off
rem ============================================================================
rem 绕过 PowerShell 执行策略启动后端（双击或在 cmd 里执行）
rem
rem 用法：
rem   run-local.cmd                使用 local profile
rem   run-local.cmd -Profile demo  指定 profile
rem
rem 依赖同目录下的 run-local.ps1，它会读取项目根目录的 .env。
rem ============================================================================
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-local.ps1" %*
endlocal
