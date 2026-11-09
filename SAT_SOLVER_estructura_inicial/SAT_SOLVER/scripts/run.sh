
#!/usr/bin/env bash

# ==========================================
# SAT SOLVER - EJECUCION EN macOS / LINUX
# ==========================================

set -e

# Ubicarse en la carpeta principal del proyecto
cd "$(dirname "$0")/.."

echo ""
echo "====================================="
echo "      SAT SOLVER - JAVA EDITION"
echo "====================================="
echo ""

# Verificar que Java esté instalado
if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: Java no está instalado."
    echo "Instala JDK 17 o superior."
    exit 1
fi

if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: No se encontró javac."
    echo "Instala un JDK 17 o superior."
    exit 1
fi

# Crear carpeta para archivos compilados
mkdir -p out

echo "[1/2] Compilando SAT SOLVER..."

# Compilar Main.java y sus dependencias
javac -encoding UTF-8 \
    -d out \
    -sourcepath src/main/java \
    src/main/java/ec/edu/satsolver/Main.java

echo ""
echo "[2/2] Iniciando servidor SAT SOLVER..."
echo ""
echo "Abre en tu navegador:"
echo "http://localhost:8080"
echo ""

# Ejecutar aplicación
java -cp out ec.edu.satsolver.Main
