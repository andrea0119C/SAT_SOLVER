
# SAT SOLVER — Requisitos del entorno

## Requisitos obligatorios

| Herramienta | Versión mínima | Uso |
|---|---|---|
| JDK de Java | 17 o posterior | Compilar (`javac`) y ejecutar (`java`) el backend y algoritmo SAT. |
| Navegador moderno | Versión actual | Mostrar HTML/CSS y ejecutar JavaScript. |
| Git | Versión reciente | Clonar, crear ramas y colaborar en el repositorio. |

**No necesitas instalar HTML, CSS ni JavaScript:** el navegador los interpreta.

## Recomendados para desarrollar

- Visual Studio Code (VS Code).
- Extensión **Extension Pack for Java** (Microsoft), para trabajar con Java en VS Code.
- Extensión **Prettier - Code formatter** (opcional), para dar formato a HTML, CSS y JavaScript.

## Dependencias del proyecto

- Java estándar (sin bibliotecas externas para resolver SAT).
- Servidor HTTP integrado en el JDK (`com.sun.net.httpserver.HttpServer`).
- No requiere Node.js, npm, Python, Spring Boot, Maven ni Gradle para la versión inicial.
- No requiere base de datos.

## Verificación del entorno

Abre una terminal en VS Code y ejecuta:

```powershell
java -version
javac -version
git --version
```

**Importante:** `java` y `javac` deben responder. Si `javac` no existe, probablemente instalaste solo un runtime o falta configurar el PATH del JDK.

## Sistemas compatibles

- Windows: ejecuta `scripts\run.bat`.
- Linux/macOS: ejecuta `bash scripts/run.sh`.

Recomendación: documentar el JDK exacto usado por el equipo en la entrega final para reproducir las pruebas.
