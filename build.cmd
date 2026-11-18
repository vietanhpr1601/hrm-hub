@echo off
setlocal
cd /d "%~dp0"
node scripts\local.mjs stop
if errorlevel 1 exit /b 1
cd /d "%~dp0hrm-backend"
call mvnw.cmd -B clean verify
if errorlevel 1 exit /b 1
cd /d "%~dp0hrm-frontend"
call npm.cmd ci --no-audit --no-fund
if errorlevel 1 exit /b 1
call npm.cmd run check
if errorlevel 1 exit /b 1
echo Build complete: hrm-backend\target\hrm-0.0.1-SNAPSHOT.jar and hrm-frontend\dist
echo Next: run .\start.cmd to start the backend and frontend.
exit /b 0
