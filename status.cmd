@echo off
setlocal
cd /d "%~dp0"
node scripts\local.mjs status
exit /b %errorlevel%

