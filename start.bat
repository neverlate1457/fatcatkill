@echo off
setlocal
chcp 65001 >nul

for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
  if not "%%A"=="" if not "%%A:~0,1"=="#" set "%%A=%%B"
)

start "FatCatKill API" cmd /k "cd fatcatkill-api && mvn spring-boot:run"
timeout /t 5 /nobreak >nul
start "FatCatKill Gateway" cmd /k "cd fatcatkill-gateway && node --env-file=../.env server.js"
start "FatCatKill Web" cmd /k "cd fatcatkill-web && npm run dev"

endlocal
