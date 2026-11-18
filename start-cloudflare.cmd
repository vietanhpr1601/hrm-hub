@echo off
setlocal
rem Mo HRM Hub ra Internet qua Cloudflare Quick Tunnel.
rem Can chay start.cmd hoac start-gmail.cmd truoc (frontend o cong 5173, /api tu chuyen sang backend 8080).
cd /d "%~dp0"
where cloudflared >nul 2>nul
if errorlevel 1 (
  if exist "%ProgramFiles(x86)%\cloudflared\cloudflared.exe" (
    set "CLOUDFLARED=%ProgramFiles(x86)%\cloudflared\cloudflared.exe"
  ) else (
    echo Chua cai cloudflared. Cai bang: winget install --id Cloudflare.cloudflared
    exit /b 1
  )
) else (
  set "CLOUDFLARED=cloudflared"
)
powershell -NoProfile -Command "if (-not (Test-NetConnection 127.0.0.1 -Port 5173 -InformationLevel Quiet -WarningAction SilentlyContinue)) { exit 1 }"
if errorlevel 1 (
  echo HRM Hub chua chay. Chay .\start.cmd hoac .\start-gmail.cmd truoc, roi chay lai file nay.
  exit /b 1
)
echo Dang mo tunnel... Dia chi https://....trycloudflare.com se hien ben duoi. Nhan Ctrl+C de dung.
"%CLOUDFLARED%" tunnel --url http://localhost:5173
exit /b %errorlevel%
