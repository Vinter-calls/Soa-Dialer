# Soa Phone to MongoDB Atlas Sync PowerShell Script
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "    Starting Soa Phone to MongoDB Atlas Sync...    " -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

node tools\sync-to-mongodb.js
