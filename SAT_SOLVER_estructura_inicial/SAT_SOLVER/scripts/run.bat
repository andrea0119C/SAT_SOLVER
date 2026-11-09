
@echo off
setlocal

REM ==========================================
REM SAT SOLVER - EJECUCION EN WINDOWS
REM ==========================================

REM Ubicarse en la raiz del proyecto
cd /d "%~dp0.."

echo.
echo =====================================
echo       SAT SOLVER - JAVA EDITION
echo =====================================
echo.

REM Crear carpeta de compilacion
if not exist "out" mkdir "out"

echo [1/2] Compilando SAT SOLVER...

REM Compilar Main.java y sus dependencias
javac -encoding UTF-8 -d "out" -sourcepath "src\main\java" "src\main\java\ec\edu\satsolver\Main.java"

REM Comprobar errores
if errorlevel 1 (
    echo.
    echo ERROR: No se pudo compilar el proyecto.
    pause
    exit /b 1
)

echo.
echo [2/2] Iniciando servidor SAT SOLVER...
echo.
echo Abre en tu navegador:
echo http://localhost:8080
echo.

REM Ejecutar la aplicacion Java
java -cp "out" ec.edu.satsolver.Main

endlocal
