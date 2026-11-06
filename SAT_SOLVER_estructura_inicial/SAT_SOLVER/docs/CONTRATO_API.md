# Contrato de API (congelar antes de trabajar en paralelo)

`POST /api/solve` recibe un objeto JSON con `formula` tipo string. Responde `satisfiable` boolean, `assignment` objeto booleano o null, `elapsedNanos` entero.

`POST /api/verify` recibe `formula` string y `assignment` objeto con todas las variables de la fórmula booleanas. Responde `valid` boolean, `elapsedNanos` entero.

En error de entrada responde HTTP 400 y `error` texto.

La interfaz transforma el texto `x1=true, x2=false` a objeto JSON antes de enviarlo. Cambios en nombres de campos requieren acuerdo P1–P4.
