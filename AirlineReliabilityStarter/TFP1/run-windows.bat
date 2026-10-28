@echo off
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
javac -encoding UTF-8 -d build\classes src\main\java\Main.java src\main\java\ReliabilityStats.java
if errorlevel 1 exit /b 1
java -cp build\classes Main %*
if errorlevel 1 exit /b 1
