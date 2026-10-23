/*
 * tablas.js - Capa de Presentacion
 *
 * En el celular, las tablas con clase "tabla-tarjetas" se muestran como
 * tarjetas (ver estilos.css): cada celda lleva al lado el nombre de su
 * columna. h:dataTable no deja ponerle un atributo a cada celda, asi que
 * este script copia el titulo de cada columna (th) a sus celdas como
 * data-label, al cargar la página y cada vez que AJAX la actualiza. Sin
 * JavaScript la tarjeta se ve igual, sin esos nombres.
 */
(function () {
    function etiquetar(tabla) {
        var titulos = Array.prototype.map.call(
            tabla.querySelectorAll("thead th"),
            function (th) { return th.textContent.trim(); });
        tabla.querySelectorAll("tbody tr").forEach(function (fila) {
            Array.prototype.forEach.call(fila.children, function (celda, i) {
                if (titulos[i] && !celda.hasAttribute("data-label")) {
                    celda.setAttribute("data-label", titulos[i]);
                }
            });
        });
    }
    function etiquetarTodas() {
        document.querySelectorAll("table.tabla-tarjetas").forEach(etiquetar);
    }
    // Una actualización parcial de JSF (f:ajax render) reemplaza la tabla
    // por una nueva, sin etiquetas: cada cambio en la página se vuelve a
    // etiquetar (solo las celdas que todavía no tienen data-label).
    function observarCambios() {
        etiquetarTodas();
        if (window.MutationObserver) {
            new MutationObserver(etiquetarTodas)
                .observe(document.body, { childList: true, subtree: true });
        }
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", observarCambios);
    } else {
        observarCambios();
    }
})();
