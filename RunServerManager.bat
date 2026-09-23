@echo off
cd /d "%~dp0"

if not exist "target\classes" mkdir "target\classes"
javac -encoding UTF-8 -d target\classes tools\ServerManager.java

start "" javaw -cp target\classes tools.ServerManager
exit
