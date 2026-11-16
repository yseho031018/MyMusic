@echo off
cd /d "%~dp0music-desktop"
echo My Music 설치 파일을 만듭니다...
set ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
set ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/
call npm install
if errorlevel 1 goto fail
set CSC_IDENTITY_AUTO_DISCOVERY=false
call npm run dist
if errorlevel 1 goto fail
echo.
echo 설치 파일이 준비되었습니다.
dir /b release\*.exe
pause
exit /b 0
:fail
echo 설치 파일을 만들지 못했습니다.
pause
exit /b 1
