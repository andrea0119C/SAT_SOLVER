# Arquitectura y responsabilidades

```text
Navegador HTML/CSS/JS
       |
       | POST /api/solve y POST /api/verify
       v
Servidor HTTP (api/) ----> Analizador (parser/) ----> Fórmula (model/)
       |                                                |
       +----------------> Solucionador DPLL (solver/) <--+
       +----------------> Verificador (verification/) <--+
```

## Por qué está separado

- `model` representa el problema independientemente de la interfaz.
- `parser` valida el lenguaje CNF de entrada y produce un modelo.
- `solver` no lee archivos, imprime HTML ni interpreta JSON: solo resuelve.
- `verification` comprueba certificados completos independientemente de DPLL.
- `api` convierte solicitudes/respuestas de texto en llamadas Java.
- `web` no resuelve SAT: presenta la información.

## Decisiones deliberadas

La fórmula se almacena como `List<int[]>` para eficiencia y facilidad de defensa: variable `i` se representa mediante `i`, negación mediante `-i`. El parser conserva un mapa de símbolos con orden de aparición.

La DPLL actual implementa propagación unitaria y backtracking; puede extenderse con pure literal y heurísticas, pero sin cambiar la API. Solo se debe describir la complejidad del algoritmo realmente implementado.

## Aspectos pendientes

Pruebas automatizadas adicionales, generador controlado de instancias, medición de memoria, experimentación reproducible y archivos de gráficas. No confundir tiempos medidos con resultados experimentales hasta ejecutar repeticiones.
