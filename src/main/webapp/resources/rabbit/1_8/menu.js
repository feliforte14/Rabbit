/*
 * menu.js - Capa de Presentacion
 *
 * En el celular y la tablet (hasta 860 px) el menú lateral pasa a ser una
 * barra arriba con el botón "Menú", que lo despliega y lo vuelve a cerrar
 * (ver estilos.css). En pantallas grandes el botón no se ve y el menú
 * queda siempre abierto al costado.
 *
 * Se cierra solo al tocar Escape o al elegir una pantalla. Sin JavaScript
 * el menú se muestra siempre (el CSS solo lo esconde con <html class="js">).
 */
(function () {
    document.addEventListener("DOMContentLoaded", function () {
        var boton = document.querySelector(".menu-boton");
        var sidebar = document.querySelector(".sidebar");
        if (!boton || !sidebar) {
            return;
        }

        function abrir(abierto) {
            sidebar.classList.toggle("menu-abierto", abierto);
            boton.setAttribute("aria-expanded", String(abierto));
        }

        boton.addEventListener("click", function () {
            abrir(!sidebar.classList.contains("menu-abierto"));
        });

        document.addEventListener("keydown", function (evento) {
            if (evento.key === "Escape" && sidebar.classList.contains("menu-abierto")) {
                abrir(false);
                boton.focus();
            }
        });

        sidebar.querySelectorAll(".sidebar-nav a").forEach(function (enlace) {
            enlace.addEventListener("click", function () {
                abrir(false);
            });
        });
    });
})();
