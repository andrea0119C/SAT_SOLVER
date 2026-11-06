
# SAT SOLVER — Instalación y ejecución

## 1. Instalar las herramientas

1. Instala un **JDK 17 o superior**; se recomienda JDK 21 LTS (por ejemplo, Eclipse Temurin: https://adoptium.net/).
2. Instala **Git** si aún no lo tienes: https://git-scm.com/downloads.
3. Abre el proyecto en **Visual Studio Code**. Recomendado: Extension Pack for Java.
4. Usa un navegador moderno (Chrome, Edge o Firefox).

> HTML, CSS y JavaScript no se instalan por separado. Tampoco se necesita `pip install`, `npm install` ni descargar bibliotecas SAT.

## 2. Obtener el proyecto

Si ya lo descargaste, abre **la carpeta raíz `SAT_SOLVER`** en VS Code (la carpeta que contiene `src`, `web` y `scripts`).

Si aún no lo descargaste:

```bash
git clone URL_DE_TU_REPOSITORIO
cd SAT_SOLVER
```

Reemplaza `URL_DE_TU_REPOSITORIO` con la URL real de tu repositorio.

## 3. Verificar Java

Desde la terminal de VS Code:

```powershell
java -version
javac -version
```

Ambos deben funcionar y mostrar versión 17 o superior.

## 4. Ejecutar el proyecto

**Windows / PowerShell:**

```powershell
.\scripts\run.bat
```

**Linux / macOS:**

```bash
bash scripts/run.sh
```

El script compila los archivos `.java` en `out/` (carpeta temporal ignorada por Git) y levanta un servidor local.

Entra desde el navegador a:

**http://localhost:8080**

## 5. Comprobación rápida

1. En el menú de inicio, pulsa **Resolver SAT**.
2. Ingresa: `(x1 | !x2) & (x2)`.
3. Pulsa **Resolver**. Debe indicar que es satisfacible.
4. Prueba también `(x1) & (!x1)`; debe indicar que es insatisfacible.

## 6. Problemas frecuentes

- **`java` o `javac` no se reconoce:** instala un JDK, revisa `PATH`, cierra y abre la terminal.
- **Puerto 8080 en uso:** detén otra aplicación que esté usando ese puerto; el proyecto permite pasar un puerto alternativo al iniciar `Main` manualmente.
- **La interfaz se ve, pero no resuelve:** abre la página desde `http://localhost:8080`, no únicamente con un archivo `file:///...`. La comunicación con Java requiere que el servidor esté iniciado.
- **No encuentra `scripts/run.bat`:** confirma que la terminal está ubicada en la raíz de `SAT_SOLVER`.

