@echo off
title Soa Phone to MongoDB Atlas Sync
cd /d "%~dp0"
echo ===================================================
echo     Starting Soa Phone to MongoDB Atlas Sync...
echo ===================================================
echo.
node tools\sync-to-mongodb.js
echo.
echo Press any key to exit...
pause >nul
