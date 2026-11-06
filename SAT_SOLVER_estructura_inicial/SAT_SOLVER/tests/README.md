# Pruebas básicas que el equipo debe ampliar

1. SAT: `(a | b) & (!a | b)` → SAT.
2. UNSAT: `(a) & (!a)` → UNSAT.
3. Unidad: `(x)` → SAT con `x=true`.
4. Verificación: `(x | y)` con x=false, y=false → false.
5. Entrada inválida: `(x | )` → HTTP 400.
6. Entrada inválida: `x & y` → HTTP 400 (faltan paréntesis).

Agregar automatización y registros reproducibles antes de entregar.
