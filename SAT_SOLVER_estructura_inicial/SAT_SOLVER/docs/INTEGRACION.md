 
# Integración web-Java y verificación SAT

## Objetivo

Integrar la interfaz web con el backend Java de SAT SOLVER
mediante solicitudes HTTP, permitiendo resolver fórmulas
CNF, verificar asignaciones booleanas y gestionar errores.

## Arquitectura de integración

El sistema sigue una arquitectura modular:

Navegador HTML/CSS/JavaScript
    |
    | Solicitudes HTTP POST
    v
Servidor Java (`api/`)
    |
    +-- Analizador CNF (`parser/`)
    |
    +-- Solucionador DPLL (`solver/`)
    |
    +-- Verificador SAT (`verification/`)
    |
    v
Respuesta JSON hacia la interfaz

Esta separación permite mantener independientes
la presentación, la comunicación HTTP y la lógica SAT.

## Componentes principales

- `AssignmentVerifier.java`: evalúa una asignación
  propuesta y determina si satisface todas las cláusulas.

- `SatHttpServer.java`: recibe las solicitudes HTTP,
  utiliza los componentes Java correspondientes y
  devuelve respuestas JSON.

- `JsonUtil.java`: interpreta y genera los datos JSON
  utilizados durante la comunicación.

- `app.js`: utiliza `fetch()` para comunicarse con Java,
  gestionar los botones y mostrar los resultados.

## Endpoints de la API

### Resolver una fórmula

`POST /api/solve`

Recibe una fórmula CNF mediante el campo `formula`.

Devuelve:

- `satisfiable`: indica SAT o UNSAT.
- `assignment`: asignación encontrada o null.
- `elapsedNanos`: tiempo de ejecución en nanosegundos.

### Verificar una asignación

`POST /api/verify`

Recibe:

- `formula`: fórmula CNF.
- `assignment`: objeto con valores booleanos.

Devuelve:

- `valid`: indica si la asignación satisface la fórmula.
- `elapsedNanos`: tiempo de verificación.

## Validaciones y manejo de errores

El servidor contempla:

- HTTP 400 para entradas inválidas.
- HTTP 405 para métodos HTTP no permitidos.
- HTTP 404 para recursos inexistentes.
- Detección de variables repetidas.
- Validación de asignaciones incompletas.
- Rechazo de valores diferentes de true y false.
- Rechazo de comas finales en asignaciones JSON.
- Límite de 60 variables y 5000 cláusulas.
- Límite de 100 KB por solicitud.

La interfaz muestra los errores recibidos del servidor
y valida el formato de las asignaciones antes de enviarlas.

## Pruebas realizadas

Se incorporaron pruebas independientes para verificar
los componentes y la comunicación HTTP.

| Archivo | Pruebas aprobadas |
|---|---:|
| `PruebasVerificador.java` | 11/11 |
| `PruebasIntegracionAPI.java` | 10/10 |
| `PruebasRobustez.java` | 13/13 |

Total: 34 pruebas aprobadas en las ejecuciones realizadas.

Las pruebas incluyen:

- Fórmulas satisfacibles e insatisfacibles.
- Asignaciones válidas e inválidas.
- Entradas incorrectas y variables repetidas.
- Validación de métodos y estados HTTP.
- Restricciones de tamaño.
- Comunicación entre DPLL y el verificador.

## Resultados de integración

Se comprobó que la interfaz puede:

- Resolver fórmulas y mostrar SAT o UNSAT.
- Presentar una asignación satisfactoria.
- Verificar asignaciones propuestas por el usuario.
- Mostrar errores de formato.
- Presentar los tiempos de ejecución y verificación.

El servidor y la interfaz intercambian información
mediante JSON, sin incorporar la lógica SAT al frontend.

## Comprobaciones finales

Para cerrar la integración se debe confirmar:

- Funcionamiento de los botones Limpiar y Ejemplos.
- Reejecución de las pruebas anteriores tras cambios.
- Ejecución completa desde una copia actualizada
  del repositorio.

Las mediciones de rendimiento y el informe técnico
consolidado corresponden a las etapas posteriores.
