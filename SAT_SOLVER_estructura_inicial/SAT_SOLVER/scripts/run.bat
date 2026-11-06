@echo off
cd /d "%~dp0.."
if not exist out mkdir out
(dir /b /s src\main\java\*.java) > out\sources.txt
javac -encoding UTF-8 -d out @out\sources.txt
if errorlevel 1 exit /b 1
java -cp out ec.edu.satsolver.Main
