@echo off
cd /d "%~dp0music-desktop"
if not exist node_modules (
  echo My Music 데스크톱 구성요소를 설치합니다...
  set ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
  call npm install
  if errorlevel 1 goto fail
)
call npm start
if errorlevel 1 goto fail
exit /b 0
:fail
echo 실행에 실패했습니다.
pause
exit /b 1
