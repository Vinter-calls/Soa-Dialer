@echo off
setlocal
title Sync Soa Phone Calls to MongoDB Atlas
echo ===================================================
echo     Starting Soa Phone Calls to MongoDB Atlas...   
echo ===================================================
echo.

cd /d "%~dp0"
node tools\sync-calls-to-mongodb.js

echo.
pause
