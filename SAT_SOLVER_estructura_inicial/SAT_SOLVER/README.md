# SAT SOLVER — Proyecto integrador EDA II

Aplicación educativa de satisfacibilidad booleana **SAT** con interfaz web (HTML/CSS/JS) y motor exacto **Java** sin bibliotecas SAT externas.

## Ejecutar

**Requisitos:** JDK 17 o posterior, terminal y navegador. Desde la carpeta raíz:

```bash
mkdir -p out
javac -encoding UTF-8 -d out src/main/java/ec/edu/satsolver/*.java src/main/java/ec/edu/satsolver/*/*.java
java -cp out ec.edu.satsolver.Main
```

Abrir http://localhost:8080. En Windows PowerShell, si los comodines `*.java` no funcionan como esperas, usa VS Code Java Extension Pack o compila desde un archivo de lista de fuentes.

## Arquitectura

- `web/`: interfaz, estilos y llamadas HTTP. **P1**
- `model/` y `parser/`: representación y validación de CNF. **P2**
- `solver/`: algoritmo exacto DPLL. **P3**
- `verification/` y `api/`: comprobación de certificados, endpoints y conexión. **P4**
- `experiment/`, `tests/`, `docs/`: rendimiento, experimentación e informe. **P5** (con aportes de todo el equipo)

## Contrato de entrada

Ejemplo: `(x1 | !x2) & (x2 | x3) & (!x1 | x3)`.

- Variables: letras seguidas de letras/números/guion bajo, por ejemplo `x1`, `A`, `condicion_2`.
- Negación: `!` ; disyunción: `|` ; conjunción: `&`.
- Cada cláusula debe ir entre paréntesis. No se aceptan fórmulas generales que no estén en CNF.
- La entrada es texto; no se usa `eval` ni código ejecutable.

### API

`POST /api/solve` con `{"formula":"(x1 | !x2) & (x2)"}` → `{"satisfiable":true,"assignment":{"x1":true,"x2":true},"elapsedNanos":...}`

`POST /api/verify` con `{"formula":"(x1 | !x2) & (x2)","assignment":{"x1":true,"x2":true}}` → `{"valid":true,"elapsedNanos":...}`

Errores de sintaxis o JSON → HTTP 400 con `{"error":"..."}`.

## Importante

Este es un **punto de partida funcional**. No se debe presentar como estudio experimental terminado. Falta implementar experimentos de cinco tamaños, análisis de memoria, gráficas, pruebas ampliadas, reflexión NotebookLM, informe final y defensa.

## Acuerdos del equipo

Cada integrante crea su rama `feature/p1-frontend`, etc. Trabajar mediante Pull Requests, sin editar módulos asignados a otros sin coordinación. Antes de integrar, probar la API y los casos SAT/UNSAT.

## Docencia

El código de producción está en Java estándar. El HTML/CSS/JS solo ofrece interacción visual. Usar el repositorio para comprender, justificar y defender cada clase.
