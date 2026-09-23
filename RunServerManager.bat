@echo off
cd /d "%~dp0"

if not exist "target\server-manager-classes" mkdir "target\server-manager-classes"
javac -encoding UTF-8 -d target\server-manager-classes tools\ServerManager.java
if errorlevel 1 exit /b 1

start "" javaw -cp target\server-manager-classes tools.ServerManager
exit
