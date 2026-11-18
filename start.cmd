@echo off
setlocal
cd /d "%~dp0"
node scripts\local.mjs start
exit /b %errorlevel%

