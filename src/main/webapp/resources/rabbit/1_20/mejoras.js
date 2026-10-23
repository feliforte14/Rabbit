/*
 * mejoras.js - Capa de Presentacion (ADR-054)
 *
 * Mejoras de uso y accesibilidad que no cambian ninguna regla de negocio y que
 * la pagina no necesita para funcionar (sin JavaScript todo se ve y anda igual):
 *
 *  - Los mensajes de error se anuncian solos a un lector de pantalla (role=alert),
 *    y los informativos tambien (role=status).
 *  - Las tablas: encabezados con scope, nombre accesible tomado del titulo
 *    de la pagina o seccion, orden al hacer clic en un encabezado, buscador,
 *    filtro por estado y vista compacta (solo en tablas con varias filas).
 *
 * Todo se hace sobre lo que JSF ya dibujo: h:dataTable no deja agregar atributos
 * ni botones por columna. Se repite cuando AJAX reemplaza una parte de la pagina.
 */
(function () {
    "use strict";

    var MINIMO_FILAS = 6; // por debajo de esto buscar/filtrar no aporta

    function anunciar() {
        document.querySelectorAll(".mensaje-error:not([role])").forEach(function (m) {
            m.setAttribute("role", "alert");
        });
        document.querySelectorAll(".mensaje-info:not([role])").forEach(function (m) {
            m.setAttribute("role", "status");
        });
    }

    function titular(tabla) {
        var nodo = tabla.previousElementSibling;
        var cuenta = 0;
        while (nodo && cuenta < 6) {
            if (/^H[1-3]$/.test(nodo.tagName)) { return nodo.textContent.trim(); }
            nodo = nodo.previousElementSibling;
            cuenta++;
        }
        var h1 = document.querySelector("h1");
        return h1 ? h1.textContent.trim() : "";
    }

    function texto(celda) {
        return celda ? celda.textContent.replace(/\s+/g, " ").trim() : "";
    }

    function numero(t) {
        // "$ 1.234,50" -> 1234.5 ; "3 kg" -> 3 ; si no es un numero, NaN
        var limpio = t.replace(/[^\d,.\-]/g, "");
        if (!/\d/.test(limpio)) { return NaN; }
        return parseFloat(limpio.replace(/\./g, "").replace(",", "."));
    }

    function ordenar(tabla, indice, ascendente) {
        var cuerpo = tabla.tBodies[0];
        var filas = Array.prototype.slice.call(cuerpo.rows);
        filas.sort(function (a, b) {
            var ta = texto(a.cells[indice]), tb = texto(b.cells[indice]);
            var na = numero(ta), nb = numero(tb);
            var r = (!isNaN(na) && !isNaN(nb)) ? na - nb : ta.localeCompare(tb, "es", { numeric: true });
            return ascendente ? r : -r;
        });
        filas.forEach(function (f) { cuerpo.appendChild(f); });
    }

    function hacerOrdenable(tabla) {
        var ths = tabla.querySelectorAll("thead th");
        Array.prototype.forEach.call(ths, function (th, i) {
            th.setAttribute("scope", "col");
            if (!texto(th) || /^acciones$/i.test(texto(th))) { return; }
            th.setAttribute("aria-sort", "none");
            th.setAttribute("tabindex", "0");
            function alternar() {
                var asc = th.getAttribute("aria-sort") !== "ascending";
                Array.prototype.forEach.call(ths, function (otro) {
                    if (otro.hasAttribute("aria-sort")) { otro.setAttribute("aria-sort", "none"); }
                });
                th.setAttribute("aria-sort", asc ? "ascending" : "descending");
                ordenar(tabla, i, asc);
            }
            th.addEventListener("click", alternar);
            th.addEventListener("keydown", function (e) {
                if (e.key === "Enter" || e.key === " ") { e.preventDefault(); alternar(); }
            });
        });
    }

    function columna(tabla, nombre) {
        var ths = tabla.querySelectorAll("thead th");
        for (var i = 0; i < ths.length; i++) {
            if (texto(ths[i]).toLowerCase() === nombre) { return i; }
        }
        return -1;
    }

    function barra(tabla) {
        var filas = tabla.tBodies[0].rows;
        var div = document.createElement("div");
        div.className = "tabla-herramientas";
        var buscador = document.createElement("input");
        buscador.type = "search";
        buscador.placeholder = "Buscar en la tabla";
        buscador.setAttribute("aria-label", "Buscar en la tabla");
        div.appendChild(buscador);

        var select = null;
        var iEstado = columna(tabla, "estado");
        if (iEstado >= 0) {
            var vistos = {};
            Array.prototype.forEach.call(filas, function (f) {
                var t = (f.cells[iEstado].textContent || "").trim().split("\n")[0].trim();
                if (t) { vistos[t] = true; }
            });
            select = document.createElement("select");
            select.setAttribute("aria-label", "Filtrar por estado");
            select.innerHTML = '<option value="">Todos los estados</option>';
            Object.keys(vistos).sort().forEach(function (t) {
                var o = document.createElement("option");
                o.value = t; o.textContent = t; select.appendChild(o);
            });
            div.appendChild(select);
        }

        var compacta = null;
        if (tabla.querySelector(".dato-secundario")) {
            compacta = document.createElement("button");
            compacta.type = "button";
            compacta.textContent = "Vista compacta";
            compacta.setAttribute("aria-pressed", "false");
            compacta.addEventListener("click", function () {
                var activa = compacta.getAttribute("aria-pressed") !== "true";
                compacta.setAttribute("aria-pressed", String(activa));
                tabla.classList.toggle("tabla-compacta", activa);
                try { localStorage.setItem("rabbit.tabla.compacta", activa ? "1" : "0"); } catch (e) { /* sin almacenamiento */ }
            });
            try {
                if (localStorage.getItem("rabbit.tabla.compacta") === "1") { compacta.click(); }
            } catch (e) { /* sin almacenamiento */ }
            div.appendChild(compacta);
        }

        var conteo = document.createElement("span");
        conteo.className = "tabla-conteo";
        conteo.setAttribute("aria-live", "polite");
        div.appendChild(conteo);

        function filtrar() {
            var q = buscador.value.trim().toLowerCase();
            var est = select ? select.value : "";
            var visibles = 0;
            Array.prototype.forEach.call(filas, function (f) {
                var ok = (!q || f.textContent.toLowerCase().indexOf(q) >= 0)
                    && (!est || (f.cells[iEstado].textContent || "").indexOf(est) >= 0);
                f.classList.toggle("fila-oculta", !ok);
                if (ok) { visibles++; }
            });
            conteo.textContent = visibles === filas.length
                ? filas.length + " filas"
                : visibles + " de " + filas.length;
        }
        buscador.addEventListener("input", filtrar);
        if (select) { select.addEventListener("change", filtrar); }
        filtrar();
        tabla.parentNode.insertBefore(div, tabla);
    }

    function tablas() {
        document.querySelectorAll("table.tabla").forEach(function (tabla) {
            if (tabla.getAttribute("data-mejorada") || !tabla.tBodies[0]) { return; }
            tabla.setAttribute("data-mejorada", "1");
            if (!tabla.getAttribute("aria-label") && !tabla.getAttribute("aria-labelledby")) {
                var t = titular(tabla);
                if (t) { tabla.setAttribute("aria-label", t); }
            }
            if (tabla.tBodies[0].rows.length >= 2) { hacerOrdenable(tabla); }
            if (tabla.tBodies[0].rows.length >= MINIMO_FILAS) { barra(tabla); }
        });
    }

    function todo() { anunciar(); tablas(); }

    function iniciar() {
        todo();
        if (window.MutationObserver) {
            var pendiente = false;
            new MutationObserver(function () {
                if (pendiente) { return; }
                pendiente = true;
                window.setTimeout(function () { pendiente = false; todo(); }, 50);
            }).observe(document.body, { childList: true, subtree: true });
        }
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", iniciar);
    } else {
        iniciar();
    }
})();
