
"use strict";

/* ==========================================
   SAT SOLVER - JAVASCRIPT DEL PANEL
   Navegación y comunicación con Java
   ========================================== */

// Actividades disponibles en el menú principal.
const ACTIVIDADES = {
    resolver: {
        titulo: "Resolver SAT",
        descripcion: "Encuentra una asignación que satisface una fórmula CNF."
    },

    verificar: {
        titulo: "Verificar asignación",
        descripcion: "Comprueba una asignación propuesta por el usuario."
    },

    ejemplos: {
        titulo: "Probar ejemplos",
        descripcion: "Ejecuta fórmulas SAT y UNSAT predeterminadas."
    },

    rendimiento: {
        titulo: "Análisis de rendimiento",
        descripcion: "Explora los resultados experimentales del algoritmo."
    },

    ayuda: {
        titulo: "Ayuda e instrucciones",
        descripcion: "Consulta la sintaxis CNF y las instrucciones."
    }
};

/* ==========================================
   1. NAVEGACIÓN
   ========================================== */

function mostrarActividad() {

    // Ejemplo: panel.html#resolver
    const actividad = window.location.hash.substring(1);

    // Si la actividad no existe, regresar al menú.
    if (!Object.prototype.hasOwnProperty.call(
        ACTIVIDADES, actividad
    )) {
        window.location.replace("index.html");
        return;
    }

    // Ocultar todas las secciones.
    document.querySelectorAll(".tool-screen")
        .forEach(seccion => {
            seccion.hidden = true;
        });

    // Mostrar únicamente la seleccionada.
    const seccionActual = document.getElementById(actividad);

    if (seccionActual) {
        seccionActual.hidden = false;
    }

    // Actualizar información del panel lateral.
    document.getElementById("sidebar-activity").textContent =
        ACTIVIDADES[actividad].titulo;

    document.getElementById("sidebar-description").textContent =
        ACTIVIDADES[actividad].descripcion;

    // Actualizar el título del navegador.
    document.title =
        ACTIVIDADES[actividad].titulo + " | SAT SOLVER";
}

/* ==========================================
   2. COMUNICACIÓN CON JAVA
   ========================================== */

async function enviarSolicitud(ruta, datos) {

    const respuesta = await fetch("/api/" + ruta, {
        method: "POST",

        headers: {
            "Content-Type": "application/json"
        },

        body: JSON.stringify(datos)
    });

    const resultado = await respuesta.json();

    if (!respuesta.ok) {
        throw new Error(
            resultado.error || "Error al procesar la solicitud."
        );
    }

    return resultado;
}

/* ==========================================
   3. FORMATO DE RESULTADOS
   ========================================== */

function formatearTiempo(nanosegundos) {
    if (typeof nanosegundos !== "number") {
        return "No disponible";
    }

    return (nanosegundos / 1000000).toFixed(4) + " ms";
}

function formatearSolucion(datos) {

    const tiempo = formatearTiempo(datos.elapsedNanos);

    if (!datos.satisfiable) {
        return (
            "RESULTADO: UNSAT\n\n" +
            "No existe una asignación satisfactoria.\n\n" +
            "Tiempo de ejecución: " + tiempo
        );
    }

    let texto = "RESULTADO: SAT\n\n";
    texto += "Asignación encontrada:\n";

    for (const [variable, valor]
        of Object.entries(datos.assignment || {})) {

        texto += variable + " = " +
            (valor ? "true" : "false") + "\n";
    }

    texto += "\nTiempo de ejecución: " + tiempo;

    return texto;
}

/* ==========================================
   4. RESOLVER SAT
   ========================================== */

async function resolverSAT(formula, resultadoElemento, boton) {

    if (!formula.trim()) {
        resultadoElemento.textContent =
            "Error: debes ingresar una fórmula CNF.";
        return;
    }

    const textoOriginal = boton.textContent;

    boton.disabled = true;
    boton.textContent = "Calculando...";

    resultadoElemento.textContent =
        "Ejecutando algoritmo SAT...";

    try {

        const datos = await enviarSolicitud("solve", {
            formula: formula
        });

        resultadoElemento.textContent =
            formatearSolucion(datos);

    } catch (error) {

        resultadoElemento.textContent =
            "Error: " + error.message;

    } finally {

        boton.disabled = false;
        boton.textContent = textoOriginal;
    }
}

/* ==========================================
   5. INTERPRETAR ASIGNACIÓN
   ========================================== */

function interpretarAsignacion(texto) {

    const asignacion = {};

    if (!texto.trim()) {
        throw new Error(
            "Debes ingresar una asignación."
        );
    }

    const elementos = texto.split(",");

    for (const elemento of elementos) {

        const coincidencia = elemento.trim().match(
            /^([a-zA-Z][a-zA-Z0-9_]*)\s*=\s*(true|false)$/i
        );

        if (!coincidencia) {
            throw new Error(
                "Formato incorrecto. Usa x1=true, x2=false"
            );
        }

        const variable = coincidencia[1];

        if (Object.prototype.hasOwnProperty.call(
            asignacion, variable
        )) {
            throw new Error(
                "Variable repetida: " + variable
            );
        }

        asignacion[variable] =
            coincidencia[2].toLowerCase() === "true";
    }

    return asignacion;
}

/* ==========================================
   6. VERIFICAR ASIGNACIÓN
   ========================================== */

async function verificarAsignacion() {

    const formula =
        document.getElementById("verify-formula").value;

    const textoAsignacion =
        document.getElementById("assignment").value;

    const resultado =
        document.getElementById("verify-result");

    const boton =
        document.getElementById("verify");

    if (!formula.trim()) {
        resultado.textContent =
            "Error: debes ingresar una fórmula CNF.";
        return;
    }

    try {

        const asignacion =
            interpretarAsignacion(textoAsignacion);

        boton.disabled = true;
        resultado.textContent = "Verificando...";

        const datos = await enviarSolicitud("verify", {
            formula: formula,
            assignment: asignacion
        });

        resultado.textContent = datos.valid
            ? "ASIGNACIÓN VÁLIDA\n\nTodas las cláusulas se satisfacen."
            : "ASIGNACIÓN NO VÁLIDA\n\nLa asignación no satisface la fórmula.";

        resultado.textContent +=
            "\n\nTiempo de verificación: " +
            formatearTiempo(datos.elapsedNanos);

    } catch (error) {

        resultado.textContent =
            "Error: " + error.message;

    } finally {

        boton.disabled = false;
    }
}

/* ==========================================
   7. EJEMPLOS
   ========================================== */

async function ejecutarEjemplo(boton) {

    const formula = boton.dataset.example;

    const resultado =
        document.getElementById("example-result");

    await resolverSAT(formula, resultado, boton);
}

/* ==========================================
   8. INICIALIZACIÓN DEL PANEL
   ========================================== */

function iniciarPanel() {

    // No ejecutar este código en index.html.
    if (!document.body.classList.contains("panel-page")) {
        return;
    }

    mostrarActividad();

    // Cambios en la URL.
    window.addEventListener(
        "hashchange",
        mostrarActividad
    );

    // Botón Resolver.
    const botonResolver = document.getElementById("solve");

    botonResolver.addEventListener("click", () => {

        const formula =
            document.getElementById("formula").value;

        const resultado =
            document.getElementById("solve-result");

        resolverSAT(formula, resultado, botonResolver);
    });

    // Botón Limpiar de Resolver.
    document.getElementById("clear-solve")
        .addEventListener("click", () => {

            document.getElementById("formula").value = "";

            document.getElementById("solve-result")
                .textContent = "Ingresa una fórmula y pulsa Resolver.";
        });

    // Botón Verificar.
    document.getElementById("verify")
        .addEventListener("click", verificarAsignacion);

    // Botón Limpiar de Verificar.
    document.getElementById("clear-verify")
        .addEventListener("click", () => {

            document.getElementById("verify-formula").value = "";
            document.getElementById("assignment").value = "";

            document.getElementById("verify-result")
                .textContent = "Aquí aparecerá la verificación.";
        });

    // Botones de ejemplos.
    document.querySelectorAll(".example-run")
        .forEach(boton => {

            boton.addEventListener("click", () => {
                ejecutarEjemplo(boton);
            });
        });
}

// El script se carga con defer.
iniciarPanel();
