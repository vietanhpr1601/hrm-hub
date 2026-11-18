@echo off
setlocal
cd /d "%~dp0"
where node.exe >nul 2>nul
if errorlevel 1 (
  echo Node.js is required. Install Node.js and reopen this terminal.
  exit /b 1
)
if not exist "node_modules\vite\bin\vite.js" (
  call npm.cmd ci
  if errorlevel 1 exit /b 1
)
call npm.cmd run dev -- %*
exit /b %errorlevel%
