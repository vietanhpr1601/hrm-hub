@echo off
setlocal
cd /d "%~dp0"
node scripts\local.mjs database
exit /b %errorlevel%

