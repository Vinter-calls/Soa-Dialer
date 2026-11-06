# Soa Phone Calls to MongoDB Atlas Sync PowerShell Script
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "   Starting Soa Phone Calls to MongoDB Atlas...   " -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

node tools\sync-calls-to-mongodb.js
