@echo off
setlocal
cd /d "%~dp0"
node scripts\local.mjs stop
exit /b %errorlevel%

